package ir.bita.esb.route;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import ir.bita.esb.cache.RouteCache;
import ir.bita.esb.config.EsbConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages Camel routes lifecycle.
 */
@Slf4j
public class RouteManager {

    private final Vertx vertx;
    private final EsbConfig config;
    private final RouteCache routeCache;
    private final CamelContext camelContext;
    private final DynamicRouteBuilder routeBuilder;
    
    @Getter
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private final Map<Long, String> routeIdToRouteIdMap = new ConcurrentHashMap<>();

    public RouteManager(Vertx vertx, EsbConfig config, RouteCache routeCache) {
        this.vertx = vertx;
        this.config = config;
        this.routeCache = routeCache;
        this.camelContext = new DefaultCamelContext();
        this.routeBuilder = new DynamicRouteBuilder(camelContext);
    }

    /**
     * Initialize routes from the provided list.
     */
    public Future<Void> initialize(List<RouteDefinition> routes) {
        Promise<Void> promise = Promise.promise();

        vertx.executeBlocking(p -> {
            try {
                log.info("Initializing {} routes for service {}", routes.size(), config.getServiceId());
                
                // Start Camel context
                camelContext.start();
                
                // Build and add routes
                for (RouteDefinition routeDef : routes) {
                    try {
                        addRoute(routeDef);
                        log.info("Route '{}' (ID: {}) added successfully", routeDef.getName(), routeDef.getRouteId());
                    } catch (Exception e) {
                        log.error("Failed to add route '{}' (ID: {})", routeDef.getName(), routeDef.getRouteId(), e);
                    }
                }
                
                initialized.set(true);
                p.complete();
            } catch (Exception e) {
                p.fail(e);
            }
        }).onSuccess(v -> promise.complete())
          .onFailure(promise::fail);

        return promise.future();
    }

    /**
     * Add a single route.
     */
    public void addRoute(RouteDefinition routeDef) throws Exception {
        String camelRouteId = "route-" + routeDef.getRouteId();
        
        // Build and add to Camel
        var builder = routeBuilder.buildRoute(routeDef, camelRouteId);
        camelContext.addRoutes(builder);
        
        // Cache the route
        routeCache.addRoute(routeDef);
        routeIdToRouteIdMap.put(routeDef.getRouteId(), camelRouteId);
    }

    /**
     * Update an existing route.
     */
    public void updateRoute(RouteDefinition routeDef) throws Exception {
        String camelRouteId = routeIdToRouteIdMap.get(routeDef.getRouteId());
        
        if (camelRouteId != null) {
            // Remove old route
            camelContext.getRouteController().stopRoute(camelRouteId);
            camelContext.removeRoute(camelRouteId);
        }
        
        // Add updated route
        addRoute(routeDef);
        log.info("Route '{}' (ID: {}) updated successfully", routeDef.getName(), routeDef.getRouteId());
    }

    /**
     * Remove a route.
     */
    public void removeRoute(Long routeId) throws Exception {
        String camelRouteId = routeIdToRouteIdMap.remove(routeId);
        
        if (camelRouteId != null) {
            camelContext.getRouteController().stopRoute(camelRouteId);
            camelContext.removeRoute(camelRouteId);
            routeCache.removeRoute(routeId);
            log.info("Route ID: {} removed successfully", routeId);
        }
    }

    /**
     * Execute a route for an incoming request.
     */
    public Future<Object> executeRoute(RouteDefinition routeDef, RoutingContext ctx) {
        Promise<Object> promise = Promise.promise();

        vertx.executeBlocking(p -> {
            try {
                String camelRouteId = routeIdToRouteIdMap.get(routeDef.getRouteId());
                if (camelRouteId == null) {
                    p.fail(new IllegalStateException("Route not found: " + routeDef.getRouteId()));
                    return;
                }

                // Create exchange and send to route
                var producerTemplate = camelContext.createProducerTemplate();
                
                // Prepare headers from HTTP request
                Map<String, Object> headers = new java.util.HashMap<>();
                ctx.request().headers().forEach(entry -> 
                    headers.put(entry.getKey(), entry.getValue()));
                headers.put("CamelHttpMethod", ctx.request().method().name());
                headers.put("CamelHttpPath", ctx.request().path());
                headers.put("CamelHttpQuery", ctx.request().query());
                headers.put("RequestId", ctx.get("requestId"));

                // Get body
                String body = ctx.body().asString();

                // Send to route
                Object result = producerTemplate.requestBodyAndHeaders(
                    "direct:" + camelRouteId, body, headers);

                // Send response
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/xml")
                        .end(result != null ? result.toString() : "");

                p.complete(result);
            } catch (Exception e) {
                p.fail(e);
            }
        }).onSuccess(promise::complete)
          .onFailure(promise::fail);

        return promise.future();
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Get Camel context for testing (package-private).
     */
    CamelContext getCamelContextForTesting() {
        return camelContext;
    }

    /**
     * Shutdown the route manager.
     */
    public Future<Void> shutdown() {
        Promise<Void> promise = Promise.promise();

        vertx.executeBlocking(p -> {
            try {
                camelContext.stop();
                p.complete();
            } catch (Exception e) {
                p.fail(e);
            }
        }).onSuccess(v -> promise.complete())
          .onFailure(promise::fail);

        return promise.future();
    }
}
