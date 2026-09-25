package ir.bita.esb.handler;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import ir.bita.esb.access.RouteAccessService;
import ir.bita.esb.config.EsbConfig;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

/**
 * Pre-flight handler: extracts client identity and calls ctx.next().
 * Camel (loaded from assembled Groovy script) owns route matching and dispatch.
 */
@Slf4j
public class RequestHandler {

    private final Vertx vertx;
    private final EsbConfig config;
    private RouteAccessService accessService;

    public RequestHandler(Vertx vertx, EsbConfig config) {
        this.vertx = vertx;
        this.config = config;
    }

    public void setAccessService(RouteAccessService accessService) {
        this.accessService = accessService;
    }

    public void handle(RoutingContext ctx) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        String path = ctx.request().path();
        String method = ctx.request().method().name();

        log.debug("Handling request [{}]: {} {}", requestId, method, path);

        ctx.put("requestId", requestId);
        ctx.put("startTime", startTime);

        String clientId = extractClientId(ctx);
        if (clientId == null) {
            sendError(ctx, 401, "UNAUTHORIZED", "Client authentication required");
            return;
        }

        if (accessService != null && !accessService.hasAccess(clientId)) {
            sendError(ctx, 403, "FORBIDDEN", "Client does not have access");
            return;
        }

        ctx.put("clientId", clientId);
        ctx.next();
    }

    private String extractClientId(RoutingContext ctx) {
        // Try X.509 certificate first
        var certs = ctx.request().sslSession();
        if (certs != null) {
            // Extract client ID from certificate
            // Implementation depends on certificate structure
        }

        // Try API key header
        String apiKey = ctx.request().getHeader("X-API-Key");
        if (apiKey != null && accessService != null) {
            return accessService.getClientIdByApiKey(apiKey);
        }

        // Try IP address
        String clientIp = getClientIp(ctx);
        if (clientIp != null && accessService != null) {
            return accessService.getClientIdByIp(clientIp);
        }

        return null;
    }

    private String getClientIp(RoutingContext ctx) {
        String xForwardedFor = ctx.request().getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = ctx.request().getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return ctx.request().remoteAddress().host();
    }

    private void sendError(RoutingContext ctx, int statusCode, String code, String message) {
        JsonObject error = new JsonObject()
                .put("error", code)
                .put("message", message)
                .put("requestId", ctx.get("requestId"))
                .put("timestamp", Instant.now().toString());

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(error.encode());
    }
}
