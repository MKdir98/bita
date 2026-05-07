package ir.bita.esb.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handler for Kubernetes health check probes.
 */
@Slf4j
public class HealthCheckHandler {

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final Instant startTime = Instant.now();

    /**
     * Liveness probe - returns 200 if the application is running.
     */
    public void handleLiveness(RoutingContext ctx) {
        JsonObject response = new JsonObject()
                .put("status", "UP")
                .put("startTime", startTime.toString())
                .put("uptime", java.time.Duration.between(startTime, Instant.now()).toSeconds() + "s");

        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }

    /**
     * Readiness probe - returns 200 only if routes are loaded and ready.
     */
    public void handleReadiness(RoutingContext ctx) {
        if (ready.get()) {
            JsonObject response = new JsonObject()
                    .put("status", "READY")
                    .put("message", "Routes loaded and ready to serve requests");

            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode());
        } else {
            JsonObject response = new JsonObject()
                    .put("status", "NOT_READY")
                    .put("message", "Routes not yet loaded");

            ctx.response()
                    .setStatusCode(503)
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode());
        }
    }

    public void setReady(boolean isReady) {
        ready.set(isReady);
        log.info("Readiness status changed to: {}", isReady);
    }

    public boolean isReady() {
        return ready.get();
    }
}
