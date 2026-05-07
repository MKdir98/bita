package ir.bita.esb.cache;

import ir.bita.esb.route.RouteDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * In-memory cache for route definitions for this Service.
 */
@Slf4j
public class RouteCache {

    private final Map<Long, RouteDefinition> routesById = new ConcurrentHashMap<>();
    private final Map<String, RouteDefinition> routesByPath = new ConcurrentHashMap<>();

    /**
     * Add or update a route.
     */
    public void addRoute(RouteDefinition route) {
        routesById.put(route.getRouteId(), route);
        
        // Index by path pattern
        String pathPattern = extractPathPattern(route);
        if (pathPattern != null) {
            routesByPath.put(pathPattern, route);
        }
        
        log.debug("Route cached: {} (ID: {})", route.getName(), route.getRouteId());
    }

    /**
     * Remove a route.
     */
    public void removeRoute(Long routeId) {
        RouteDefinition route = routesById.remove(routeId);
        
        if (route != null) {
            String pathPattern = extractPathPattern(route);
            if (pathPattern != null) {
                routesByPath.remove(pathPattern);
            }
        }
        
        log.debug("Route removed from cache: {}", routeId);
    }

    /**
     * Get route by ID.
     */
    public RouteDefinition getRoute(Long routeId) {
        return routesById.get(routeId);
    }

    /**
     * Find route by request path and method.
     */
    public RouteDefinition findRoute(String path, String method) {
        // First try exact match
        RouteDefinition exact = routesByPath.get(path);
        if (exact != null && exact.isActive()) {
            return exact;
        }

        // Try pattern matching
        for (Map.Entry<String, RouteDefinition> entry : routesByPath.entrySet()) {
            String pattern = entry.getKey();
            RouteDefinition route = entry.getValue();
            
            if (!route.isActive()) continue;
            
            if (matchesPath(path, pattern)) {
                return route;
            }
        }

        return null;
    }

    /**
     * Get all routes.
     */
    public Collection<RouteDefinition> getAllRoutes() {
        return routesById.values();
    }

    /**
     * Clear all routes.
     */
    public void clear() {
        routesById.clear();
        routesByPath.clear();
        log.info("Route cache cleared");
    }

    public int size() {
        return routesById.size();
    }

    private String extractPathPattern(RouteDefinition route) {
        if (route.getInputConfig() != null) {
            Object path = route.getInputConfig().get("path");
            if (path != null) {
                return path.toString();
            }
            
            Object servicePath = route.getInputConfig().get("servicePath");
            if (servicePath != null) {
                return servicePath.toString();
            }
        }
        
        // Try to extract from input URI
        if (route.getInputUri() != null) {
            String uri = route.getInputUri();
            int queryStart = uri.indexOf('?');
            if (queryStart > 0) {
                uri = uri.substring(0, queryStart);
            }
            // Extract path after protocol
            int pathStart = uri.indexOf("://");
            if (pathStart > 0) {
                uri = uri.substring(pathStart + 3);
                int slashIndex = uri.indexOf('/');
                if (slashIndex > 0) {
                    return uri.substring(slashIndex);
                }
            }
        }
        
        return "/route/" + route.getRouteId();
    }

    private boolean matchesPath(String requestPath, String pattern) {
        // Convert pattern with {param} to regex
        String regex = pattern
                .replaceAll("\\{[^}]+\\}", "[^/]+")
                .replace("*", ".*");
        
        return Pattern.matches(regex, requestPath);
    }
}
