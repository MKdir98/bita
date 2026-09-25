package ir.bita.esb.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.core.json.JsonObject;
import ir.bita.esb.config.EsbConfig;
import ir.bita.esb.handler.HealthCheckHandler;
import ir.bita.esb.handler.MetricsHandler;
import ir.bita.esb.handler.RequestHandler;
import ir.bita.esb.sync.SyncService;
import lombok.extern.slf4j.Slf4j;

/**
 * Main HTTP Server Verticle for handling incoming requests.
 */
@Slf4j
public class HttpServerVerticle extends AbstractVerticle {

    private final EsbConfig config;
    private final SyncService syncService;
    private HttpServer server;
    private final HealthCheckHandler healthCheckHandler = new HealthCheckHandler();
    private RequestHandler requestHandler;

    public HttpServerVerticle(EsbConfig config, SyncService syncService) {
        this.config = config;
        this.syncService = syncService;
    }

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

        // Internal hot-reload endpoint — called by ESM after Groovy script update
        router.post("/internal/reload").handler(ctx -> {
            syncService.reload()
                    .onSuccess(v -> ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("status", "reloaded").encode()))
                    .onFailure(err -> {
                        log.error("Hot-reload failed", err);
                        ctx.response()
                                .setStatusCode(500)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("error", err.getMessage()).encode());
                    });
        });

        // Internal full re-sync — called by ESM after a consumer's access is granted or revoked:
        // clients, access rules and consumer certificates are fetched again
        router.post("/internal/resync").handler(ctx -> {
            syncService.fullSync()
                    .onSuccess(v -> ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("status", "resynced").encode()))
                    .onFailure(err -> ctx.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", String.valueOf(err.getMessage())).encode()));
        });

        // Internal trial run of a proposed template — called by ESM before the template joins the
        // catalog (on the sandbox ESB, which runs no service): {"script": ..., "variables": {...}}
        router.post("/internal/trial-script").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            java.util.Map<String, Object> vars = body.getJsonObject("variables", new JsonObject()).getMap();
            ir.bita.esb.sync.ScriptTrial.run(vertx, body.getString("script", ""), vars)
                    .onComplete(r -> ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end((r.succeeded() ? r.result()
                                    : new JsonObject().put("ok", false).put("stage", "trial")
                                            .put("error", String.valueOf(r.cause()))).encode()));
        });

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
