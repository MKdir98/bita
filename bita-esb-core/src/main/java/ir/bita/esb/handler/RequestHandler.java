package ir.bita.esb.handler;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import ir.bita.esb.cache.RouteCache;
import ir.bita.esb.cache.AccessCache;
import ir.bita.esb.cache.ClientCache;
import ir.bita.esb.config.EsbConfig;
import ir.bita.esb.route.RouteManager;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

/**
 * Main request handler that routes incoming requests to appropriate Camel routes.
 */
@Slf4j
public class RequestHandler {

    private final Vertx vertx;
    private final EsbConfig config;
    private RouteManager routeManager;
    private RouteCache routeCache;
    private AccessCache accessCache;
    private ClientCache clientCache;

    public RequestHandler(Vertx vertx, EsbConfig config) {
        this.vertx = vertx;
        this.config = config;
    }

    public void setRouteManager(RouteManager routeManager) {
        this.routeManager = routeManager;
    }

    public void setCaches(RouteCache routeCache, AccessCache accessCache, ClientCache clientCache) {
        this.routeCache = routeCache;
        this.accessCache = accessCache;
        this.clientCache = clientCache;
    }

    public void handle(RoutingContext ctx) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        String path = ctx.request().path();
        String method = ctx.request().method().name();

        log.debug("Handling request [{}]: {} {}", requestId, method, path);

        ctx.put("requestId", requestId);
        ctx.put("startTime", startTime);

        // Check if routes are loaded
        if (routeManager == null || !routeManager.isInitialized()) {
            sendError(ctx, 503, "SERVICE_UNAVAILABLE", "Service is starting up, routes not yet loaded");
            return;
        }

        // Find matching route
        var routeDefinition = routeCache != null ? routeCache.findRoute(path, method) : null;
        
        if (routeDefinition == null) {
            sendError(ctx, 404, "NOT_FOUND", "No route found for path: " + path);
            return;
        }

        // Authenticate client
        String clientId = extractClientId(ctx);
        if (clientId == null) {
            sendError(ctx, 401, "UNAUTHORIZED", "Client authentication required");
            return;
        }

        // Check access
        if (accessCache != null && !accessCache.hasAccess(clientId, routeDefinition.getRouteId())) {
            sendError(ctx, 403, "FORBIDDEN", "Client does not have access to this route");
            return;
        }

        // Check rate limit
        if (accessCache != null && !accessCache.checkRateLimit(clientId, routeDefinition.getRouteId())) {
            sendError(ctx, 429, "TOO_MANY_REQUESTS", "Rate limit exceeded");
            return;
        }

        // Execute the route
        try {
            routeManager.executeRoute(routeDefinition, ctx)
                    .onSuccess(result -> {
                        long duration = java.time.Duration.between(startTime, Instant.now()).toMillis();
                        log.info("Request [{}] completed in {}ms", requestId, duration);
                    })
                    .onFailure(err -> {
                        log.error("Request [{}] failed", requestId, err);
                        sendError(ctx, 500, "INTERNAL_ERROR", err.getMessage());
                    });
        } catch (Exception e) {
            log.error("Error executing route for request [{}]", requestId, e);
            sendError(ctx, 500, "INTERNAL_ERROR", e.getMessage());
        }
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
        if (apiKey != null && clientCache != null) {
            return clientCache.getClientIdByApiKey(apiKey);
        }

        // Try IP address
        String clientIp = getClientIp(ctx);
        if (clientIp != null && clientCache != null) {
            return clientCache.getClientIdByIp(clientIp);
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
