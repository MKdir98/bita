package ir.bita.esb;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import ir.bita.esb.config.EsbConfig;
import ir.bita.esb.verticle.HttpServerVerticle;
import lombok.extern.slf4j.Slf4j;

/**
 * Main entry point for ESB Core application.
 * Each pod handles only ONE Service (identified by SERVICE_ID environment variable).
 */
@Slf4j
public class EsbCoreApplication {

    public static void main(String[] args) {
        // Load configuration
        EsbConfig config = EsbConfig.load();
        
        log.info("Starting ESB Core for Service ID: {}", config.getServiceId());
        
        // Create Vertx with metrics
        VertxOptions vertxOptions = new VertxOptions()
                .setMetricsOptions(new MicrometerMetricsOptions()
                        .setPrometheusOptions(new VertxPrometheusOptions()
                                .setEnabled(true)
                                .setPublishQuantiles(true))
                        .setEnabled(true));

        Vertx vertx = Vertx.vertx(vertxOptions);

        // Deploy HTTP Server verticle
        vertx.deployVerticle(new HttpServerVerticle(config))
                .onSuccess(id -> {
                    log.info("ESB Core started successfully. Verticle ID: {}", id);
                    log.info("HTTP Server listening on port {}", config.getHttpPort());
                })
                .onFailure(err -> {
                    log.error("Failed to start ESB Core", err);
                    vertx.close();
                    System.exit(1);
                });

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down ESB Core...");
            vertx.close()
                    .onSuccess(v -> log.info("ESB Core shut down successfully"))
                    .onFailure(err -> log.error("Error during shutdown", err));
        }));
    }
}
