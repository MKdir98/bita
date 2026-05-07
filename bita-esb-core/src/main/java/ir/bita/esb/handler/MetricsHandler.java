package ir.bita.esb.handler;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.ext.web.RoutingContext;
import io.vertx.micrometer.backends.BackendRegistries;

/**
 * Handler for Prometheus metrics endpoint.
 */
public class MetricsHandler {

    public static void handle(RoutingContext ctx) {
        PrometheusMeterRegistry registry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();
        
        if (registry != null) {
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/plain")
                    .end(registry.scrape());
        } else {
            ctx.response()
                    .setStatusCode(500)
                    .end("Metrics registry not available");
        }
    }
}
