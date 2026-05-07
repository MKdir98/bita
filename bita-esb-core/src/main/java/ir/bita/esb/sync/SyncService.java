package ir.bita.esb.sync;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import ir.bita.esb.cache.AccessCache;
import ir.bita.esb.cache.ClientCache;
import ir.bita.esb.cache.RouteCache;
import ir.bita.esb.config.EsbConfig;
import ir.bita.esb.route.RouteDefinition;
import ir.bita.esb.route.RouteManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Service for synchronizing data from ESM Backend.
 */
@Slf4j
public class SyncService {

    private final Vertx vertx;
    private final EsbConfig config;
    private final EsmApiClient esmApiClient;
    private final RouteCache routeCache;
    private final AccessCache accessCache;
    private final ClientCache clientCache;
    private final RouteManager routeManager;

    public SyncService(Vertx vertx, EsbConfig config, RouteCache routeCache, 
                       AccessCache accessCache, ClientCache clientCache, RouteManager routeManager) {
        this.vertx = vertx;
        this.config = config;
        this.esmApiClient = new EsmApiClient(config);
        this.routeCache = routeCache;
        this.accessCache = accessCache;
        this.clientCache = clientCache;
        this.routeManager = routeManager;
    }

    /**
     * Perform full sync from ESM.
     */
    public Future<Void> fullSync() {
        Promise<Void> promise = Promise.promise();

        vertx.executeBlocking(p -> {
            try {
                log.info("Starting full sync for service {}", config.getServiceId());

                // Clear existing caches
                routeCache.clear();
                accessCache.clear();
                clientCache.clear();

                // Fetch all data
                EsmApiClient.FullSyncData data = esmApiClient.fetchFullSyncData();

                // Load clients first
                if (data.getClients() != null) {
                    for (ClientCache.ClientInfo client : data.getClients()) {
                        clientCache.putClient(client);
                    }
                    log.info("Synced {} clients", data.getClients().size());
                }

                // Load access rules
                if (data.getAccessRules() != null) {
                    for (AccessCache.AccessRule rule : data.getAccessRules()) {
                        accessCache.putAccess(rule);
                    }
                    log.info("Synced {} access rules", data.getAccessRules().size());
                }

                // Load routes
                if (data.getRoutes() != null) {
                    routeManager.initialize(data.getRoutes())
                            .onSuccess(v -> {
                                log.info("Full sync completed: {} routes loaded", data.getRoutes().size());
                                p.complete();
                            })
                            .onFailure(err -> {
                                log.error("Failed to initialize routes", err);
                                p.fail(err);
                            });
                } else {
                    log.info("Full sync completed: no routes found");
                    p.complete();
                }
            } catch (Exception e) {
                log.error("Full sync failed", e);
                p.fail(e);
            }
        }).onSuccess(v -> promise.complete())
          .onFailure(promise::fail);

        return promise.future();
    }

    /**
     * Sync a single route from ESM.
     */
    public void syncRoute(Long routeId) {
        vertx.executeBlocking(p -> {
            try {
                List<RouteDefinition> routes = esmApiClient.fetchRoutes();
                RouteDefinition route = routes.stream()
                        .filter(r -> r.getRouteId().equals(routeId))
                        .findFirst()
                        .orElse(null);

                if (route != null) {
                    if (routeCache.getRoute(routeId) != null) {
                        routeManager.updateRoute(route);
                    } else {
                        routeManager.addRoute(route);
                    }
                    log.info("Route {} synced successfully", routeId);
                }
                p.complete();
            } catch (Exception e) {
                log.error("Failed to sync route {}", routeId, e);
                p.fail(e);
            }
        });
    }

    /**
     * Remove a route.
     */
    public void removeRoute(Long routeId) {
        vertx.executeBlocking(p -> {
            try {
                routeManager.removeRoute(routeId);
                log.info("Route {} removed", routeId);
                p.complete();
            } catch (Exception e) {
                log.error("Failed to remove route {}", routeId, e);
                p.fail(e);
            }
        });
    }

    /**
     * Sync access rules for a client.
     */
    public void syncAccess(String clientId, Long routeId) {
        vertx.executeBlocking(p -> {
            try {
                List<AccessCache.AccessRule> rules = esmApiClient.fetchAccessRules();
                
                rules.stream()
                        .filter(r -> r.getClientId().equals(clientId) && 
                                     (routeId == null || r.getRouteId().equals(routeId)))
                        .forEach(accessCache::putAccess);
                
                log.info("Access synced for client {}", clientId);
                p.complete();
            } catch (Exception e) {
                log.error("Failed to sync access for client {}", clientId, e);
                p.fail(e);
            }
        });
    }

    /**
     * Remove access for a client/route.
     */
    public void removeAccess(String clientId, Long routeId) {
        accessCache.removeAccess(clientId, routeId);
        log.info("Access removed for client {} to route {}", clientId, routeId);
    }

    /**
     * Sync a client.
     */
    public void syncClient(String clientId) {
        vertx.executeBlocking(p -> {
            try {
                List<ClientCache.ClientInfo> clients = esmApiClient.fetchClients(List.of(clientId));
                
                if (!clients.isEmpty()) {
                    clientCache.putClient(clients.get(0));
                    log.info("Client {} synced", clientId);
                }
                p.complete();
            } catch (Exception e) {
                log.error("Failed to sync client {}", clientId, e);
                p.fail(e);
            }
        });
    }

    /**
     * Remove a client.
     */
    public void removeClient(String clientId) {
        clientCache.removeClient(clientId);
        log.info("Client {} removed from cache", clientId);
    }
}
