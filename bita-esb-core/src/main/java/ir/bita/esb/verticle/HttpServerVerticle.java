package ir.bita.esb.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import ir.bita.esb.config.EsbConfig;
import ir.bita.esb.handler.HealthCheckHandler;
import ir.bita.esb.handler.MetricsHandler;
import ir.bita.esb.handler.RequestHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Main HTTP Server Verticle for handling incoming requests.
 */
@Slf4j
@RequiredArgsConstructor
public class HttpServerVerticle extends AbstractVerticle {

    private final EsbConfig config;
    private HttpServer server;
    private final HealthCheckHandler healthCheckHandler = new HealthCheckHandler();
    private RequestHandler requestHandler;

    @Override
    public void start(Promise<Void> startPromise) {
        requestHandler = new RequestHandler(vertx, config);
        
        Router router = createRouter();

        HttpServerOptions options = new HttpServerOptions()
                .setPort(config.getHttpPort())
                .setHost("0.0.0.0")
                .setCompressionSupported(true)
                .setIdleTimeout(30);

        server = vertx.createHttpServer(options);

        server.requestHandler(router)
                .listen()
                .onSuccess(s -> {
                    log.info("HTTP Server started on port {}", config.getHttpPort());
                    startPromise.complete();
                })
                .onFailure(err -> {
                    log.error("Failed to start HTTP Server", err);
                    startPromise.fail(err);
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (server != null) {
            server.close()
                    .onSuccess(v -> {
                        log.info("HTTP Server stopped");
                        stopPromise.complete();
                    })
                    .onFailure(stopPromise::fail);
        } else {
            stopPromise.complete();
        }
    }

    private Router createRouter() {
        Router router = Router.router(vertx);

        // Global handlers
        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());

        // Health check endpoints
        router.get("/health/live").handler(healthCheckHandler::handleLiveness);
        router.get("/health/ready").handler(healthCheckHandler::handleReadiness);

        // Metrics endpoint
        router.get("/metrics").handler(MetricsHandler::handle);

        // Service routes - catch all for dynamic routing
        router.route("/*").handler(requestHandler::handle);

        return router;
    }

    /**
     * Mark the server as ready (routes loaded).
     */
    public void setReady(boolean ready) {
        healthCheckHandler.setReady(ready);
    }
}
