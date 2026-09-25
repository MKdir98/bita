package ir.bita.esb;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import ir.bita.esb.access.DefaultRouteAccessService;
import ir.bita.esb.cache.AccessCache;
import ir.bita.esb.cache.ClientCache;
import ir.bita.esb.config.EsbConfig;
import ir.bita.esb.sync.SyncService;
import ir.bita.esb.verticle.HttpServerVerticle;
import lombok.extern.slf4j.Slf4j;

/**
 * Main entry point for ESB Core application.
 * Each pod handles only ONE Service (identified by SERVICE_ID environment variable).
 */
@Slf4j
public class EsbCoreApplication {

    /** A started ESB instance: the same wiring {@link #main} runs, exposed so it can be stopped. */
    public record Running(Vertx vertx, SyncService syncService, EsbConfig config) {
        public Future<Void> close() {
            return vertx.close();
        }
    }

    public static void main(String[] args) {
        EsbConfig config = EsbConfig.load();

        start(config)
                .onSuccess(running -> Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    log.info("Shutting down ESB Core...");
                    running.close()
                            .onSuccess(v -> log.info("ESB Core shut down successfully"))
                            .onFailure(err -> log.error("Error during shutdown", err));
                })))
                .onFailure(err -> System.exit(1));
    }

    /** Boots one ESB instance: HTTP server, then a full sync of the service's config from ESM. */
    public static Future<Running> start(EsbConfig config) {
        log.info("Starting ESB Core for Service ID: {}", config.getServiceId());

        VertxOptions vertxOptions = new VertxOptions()
                .setMetricsOptions(new MicrometerMetricsOptions()
                        .setPrometheusOptions(new VertxPrometheusOptions()
                                .setEnabled(true)
                                .setPublishQuantiles(true))
                        .setEnabled(true));

        Vertx vertx = Vertx.vertx(vertxOptions);

        AccessCache accessCache = new AccessCache();
        ClientCache clientCache = new ClientCache();
        DefaultRouteAccessService accessService = new DefaultRouteAccessService(config, accessCache, clientCache);
        SyncService syncService = new SyncService(vertx, config, accessCache, clientCache, accessService);

        return vertx.deployVerticle(new HttpServerVerticle(config, syncService))
                .compose(id -> {
                    if (config.isSandbox()) {
                        log.info("HTTP Server started (id={}) in sandbox mode: trial runs only, no service", id);
                        return Future.<Void>succeededFuture();
                    }
                    log.info("HTTP Server started (id={}), running full sync...", id);
                    return syncService.fullSync();
                })
                .map(v -> {
                    log.info("ESB Core ready. Service ID: {}, port: {}", config.getServiceId(), config.getHttpPort());
                    return new Running(vertx, syncService, config);
                })
                .recover(err -> {
                    log.error("Failed to start ESB Core", err);
                    return vertx.close().transform(ignored -> Future.failedFuture(err));
                });
    }
}
