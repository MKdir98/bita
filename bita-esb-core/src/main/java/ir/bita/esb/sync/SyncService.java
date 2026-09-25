package ir.bita.esb.sync;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import ir.bita.esb.access.RouteAccessService;
import ir.bita.esb.cache.AccessCache;
import ir.bita.esb.cache.ClientCache;
import ir.bita.esb.config.EsbConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.net.BindException;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service for synchronizing data from ESM Backend.
 */
@Slf4j
public class SyncService {

    private final Vertx vertx;
    private final EsbConfig config;
    private final EsmApiClient esmApiClient;
    private final AccessCache accessCache;
    private final ClientCache clientCache;
    private final RouteAccessService accessService;

    /** The script currently serving traffic and the Vert.x deployment that owns everything it opened. */
    private volatile LoadedScript active;

    /** Consumer certificates the running script was started with (its trust store is fixed at start). */
    private volatile List<String> appliedCerts = List.of();

    /**
     * The gateway listener is owned by the ESB, not by a script, so it survives a version swap:
     * a script only registers handlers on the {@code gatewayRouter} it is given, and a swap just
     * changes which script's router this listener dispatches to.
     */
    private volatile HttpServer gatewayServer;
    private volatile int gatewayPort = -1;

    private static final long DRAIN_TIMEOUT_MS = 10_000;

    private record LoadedScript(String script, Map<String, Object> variableValues,
                                String deploymentId, ScriptVerticle verticle) {
        Router router() {
            return verticle.gatewayRouter() == null || verticle.gatewayRouter().getRoutes().isEmpty()
                    ? null : verticle.gatewayRouter();
        }

        AtomicInteger inflight() {
            return verticle.inflight();
        }
    }

    public SyncService(Vertx vertx, EsbConfig config,
                       AccessCache accessCache, ClientCache clientCache,
                       RouteAccessService accessService) {
        this.vertx = vertx;
        this.config = config;
        this.esmApiClient = new EsmApiClient(config);
        this.accessCache = accessCache;
        this.clientCache = clientCache;
        this.accessService = accessService;
    }

    /**
     * Full sync: load clients/access from ESM, then fetch and evaluate the assembled Groovy script.
     */
    public Future<Void> fullSync() {
        return vertx.<EsmApiClient.ServiceConfigPayload>executeBlocking(() -> {
            log.info("Starting full sync for service {}", config.getServiceId());

            accessCache.clear();
            clientCache.clear();

            EsmApiClient.FullSyncData data = esmApiClient.fetchFullSyncData();

            if (data.getClients() != null) {
                data.getClients().stream()
                        .filter(EsmApiClient.ClientData::isActive)
                        .map(this::toClientInfo)
                        .forEach(clientCache::putClient);
                log.info("Synced {} clients", data.getClients().size());
            }
            if (data.getAccessRules() != null) {
                data.getAccessRules().stream()
                        .filter(EsmApiClient.AccessData::isCurrentlyValid)
                        .map(this::toAccessRule)
                        .forEach(accessCache::putAccess);
                log.info("Synced {} access rules", data.getAccessRules().size());
            }
            return fetchConfigAndTrust();
        }).compose(this::apply)
          .onFailure(e -> log.error("Full sync failed", e));
    }

    /**
     * Hot-reload: re-fetch the service's Groovy script and swap it in. The running script is only
     * replaced once the new one has started; if the new one fails, the running one keeps serving.
     */
    public Future<Void> reload() {
        log.info("Hot-reloading routes for service {}", config.getServiceId());
        return vertx.executeBlocking(this::fetchConfigAndTrust)
                .compose(this::apply)
                .onSuccess(v -> log.info("Routes hot-reloaded for service {}", config.getServiceId()))
                .onFailure(e -> log.error("Hot-reload failed, previous routes kept", e));
    }

    /** The Camel context of the script currently serving traffic, or null if none is loaded. */
    public CamelContext getCamelContext() {
        LoadedScript current = active;
        return current == null ? null : current.verticle().camelContext();
    }

    /** The service's config, with the consumer certificates it trusts brought up to date first. */
    private EsmApiClient.ServiceConfigPayload fetchConfigAndTrust() throws Exception {
        EsmApiClient.ServiceConfigPayload payload = esmApiClient.fetchServiceConfig();
        accessService.updateAuthorizedClientCertificates(payload.getAuthorizedClientCertPems());
        return payload;
    }

    private Future<Void> apply(EsmApiClient.ServiceConfigPayload payload) {
        String script = payload.getAssembledScript();
        Map<String, Object> vars = payload.getVariableValues();
        List<String> certs = payload.getAuthorizedClientCertPems() == null ? List.of() : payload.getAuthorizedClientCertPems();
        LoadedScript current = active;

        if (script == null || script.isBlank()) {
            log.warn("No assembled script for service {} — no routes loaded", config.getServiceId());
            active = null;
            return current == null ? Future.succeededFuture() : vertx.undeploy(current.deploymentId());
        }
        // a changed set of granted consumers also restarts the script: a WS-Security script builds
        // its trust store when it starts, so a revoked certificate would otherwise stay trusted
        if (current != null && current.script().equals(script) && Objects.equals(current.variableValues(), vars)
                && appliedCerts.equals(certs)) {
            return Future.succeededFuture();
        }

        // compile first: a script that does not even parse never touches the running routes
        return compile(script).compose(scriptClass ->
                deploy(scriptClass, script, vars)
                        .compose(next -> swapIn(current, next))
                        .recover(err -> current != null && isResourceConflict(err)
                                ? replaceStopFirst(current, scriptClass, script, vars, err)
                                : Future.failedFuture(err)))
                .onSuccess(v -> appliedCerts = List.copyOf(certs));
    }

    /**
     * Some resources cannot overlap (e.g. a CXF endpoint on the same Jetty port and path), so a
     * new-alongside-old start fails. Only for that case: stop the old one, start the new one, and
     * if that still fails, bring the old one back.
     */
    private Future<Void> replaceStopFirst(LoadedScript current, Class<? extends Script> scriptClass,
                                          String script, Map<String, Object> vars, Throwable firstError) {
        log.info("New script could not start alongside the running one ({}); replacing stop-first",
                firstError.getMessage());
        active = null;
        return vertx.undeploy(current.deploymentId())
                .compose(v -> deploy(scriptClass, script, vars))
                .compose(next -> swapIn(null, next))
                .recover(err -> compile(current.script())
                        .compose(cls -> deploy(cls, current.script(), current.variableValues()))
                        .compose(restored -> swapIn(null, restored))
                        .transform(restoreResult -> {
                            if (restoreResult.failed()) {
                                log.error("Could not restore previous routes", restoreResult.cause());
                            }
                            return Future.<Void>failedFuture(err);
                        }));
    }

    private Future<Void> swapIn(LoadedScript previous, LoadedScript next) {
        Future<Void> gatewayReady;
        try {
            gatewayReady = next.router() == null
                    ? Future.succeededFuture()
                    : ensureGateway(gatewayPortOf(next));
        } catch (RuntimeException e) {
            gatewayReady = Future.failedFuture(e);
        }
        return gatewayReady
                .onFailure(err -> vertx.undeploy(next.deploymentId()))
                .compose(v -> {
                    active = next;
                    return previous == null ? Future.<Void>succeededFuture() : retire(previous);
                });
    }

    /** Waits for the old version's in-flight gateway requests to finish, then undeploys it. */
    private Future<Void> retire(LoadedScript old) {
        Promise<Void> drained = Promise.promise();
        long deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS;
        if (old.inflight().get() == 0) {
            drained.complete();
        } else {
            vertx.setPeriodic(10, id -> {
                if (old.inflight().get() == 0 || System.currentTimeMillis() > deadline) {
                    vertx.cancelTimer(id);
                    if (old.inflight().get() > 0) {
                        log.warn("Retiring previous routes with {} requests still in flight", old.inflight().get());
                    }
                    drained.tryComplete();
                }
            });
        }
        return drained.future().compose(v -> vertx.undeploy(old.deploymentId()));
    }

    private Future<Void> ensureGateway(int port) {
        if (gatewayServer != null && gatewayPort == port) {
            return Future.succeededFuture();
        }
        HttpServer previousServer = gatewayServer;
        return vertx.createHttpServer()
                .requestHandler(this::dispatchToActive)
                .listen(port)
                .map(server -> {
                    gatewayServer = server;
                    gatewayPort = port;
                    if (previousServer != null) {
                        previousServer.close();
                    }
                    log.info("Gateway listening on port {} for service {}", port, config.getServiceId());
                    return null;
                });
    }

    private void dispatchToActive(HttpServerRequest req) {
        LoadedScript current = active;
        Router router = current == null ? null : current.router();
        if (router == null) {
            req.response().setStatusCode(503).end("No active route for this service");
            return;
        }
        AtomicInteger inflight = current.inflight();
        inflight.incrementAndGet();
        AtomicBoolean done = new AtomicBoolean();
        Runnable finished = () -> {
            if (done.compareAndSet(false, true)) {
                inflight.decrementAndGet();
            }
        };
        req.response().endHandler(v -> finished.run());
        req.response().closeHandler(v -> finished.run());
        router.handle(req);
    }

    private static int gatewayPortOf(LoadedScript script) {
        Object port = script.variableValues() == null ? null : script.variableValues().get("gwPort");
        if (port == null) {
            throw new IllegalStateException("script registers gateway routes but gwPort is not set");
        }
        return Integer.parseInt(String.valueOf(port));
    }

    private Future<Class<? extends Script>> compile(String script) {
        return vertx.executeBlocking(() ->
                new GroovyShell(Thread.currentThread().getContextClassLoader()).parse(script).getClass());
    }

    private Future<LoadedScript> deploy(Class<? extends Script> scriptClass, String script, Map<String, Object> vars) {
        ScriptVerticle verticle = new ScriptVerticle(scriptClass, vars, accessService);
        return vertx.deployVerticle(verticle)
                .map(id -> new LoadedScript(script, vars, id, verticle));
    }

    private static boolean isResourceConflict(Throwable err) {
        for (Throwable t = err; t != null; t = t.getCause()) {
            if (t instanceof BindException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("already in use") || msg.contains("Address in use"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-fetch all access rules and update cache entries for the given client.
     */
    public void syncAccess(String clientId, Long ignoredRouteId) {
        vertx.executeBlocking(p -> {
            try {
                List<EsmApiClient.AccessData> rules = esmApiClient.fetchAccessRules();
                rules.stream()
                        .filter(r -> r.getClientId() != null && clientId.equals(String.valueOf(r.getClientId())))
                        .filter(EsmApiClient.AccessData::isCurrentlyValid)
                        .map(this::toAccessRule)
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
     * Remove access for a client (all service rules).
     */
    public void removeAccess(String clientId, Long ignoredRouteId) {
        // serviceId is stored as routeId in the cache key; clear by evicting any matching entry
        accessCache.removeAccess(clientId, config.getServiceId());
        log.info("Access removed for client {}", clientId);
    }

    /**
     * Re-fetch all clients and update cache for the given client ID.
     */
    public void syncClient(String clientId) {
        vertx.executeBlocking(p -> {
            try {
                List<EsmApiClient.ClientData> clients = esmApiClient.fetchClients();
                clients.stream()
                        .filter(c -> clientId.equals(String.valueOf(c.getId())))
                        .findFirst()
                        .map(this::toClientInfo)
                        .ifPresent(info -> {
                            clientCache.putClient(info);
                            log.info("Client {} synced", clientId);
                        });
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

    private ClientCache.ClientInfo toClientInfo(EsmApiClient.ClientData c) {
        List<ClientCache.Credential> creds = c.getCredentials() == null ? List.of() :
                c.getCredentials().stream()
                        .map(cr -> ClientCache.Credential.builder()
                                .type(cr.getCredentialType())
                                .value(cr.getCredentialValue())
                                .active(cr.isActive())
                                .build())
                        .collect(Collectors.toList());
        return ClientCache.ClientInfo.builder()
                .clientId(String.valueOf(c.getId()))
                .name(c.getName())
                .credentials(creds)
                .build();
    }

    private AccessCache.AccessRule toAccessRule(EsmApiClient.AccessData a) {
        java.time.Instant expiresAt = a.getValidUntil() != null
                ? a.getValidUntil().toInstant(ZoneOffset.UTC)
                : null;
        return AccessCache.AccessRule.builder()
                .clientId(String.valueOf(a.getClientId()))
                .routeId(a.getServiceId())  // serviceId stored as routeId — no per-route model
                .rateLimit(a.getCustomRateLimit())
                .expiresAt(expiresAt)
                .active(true)
                .build();
    }
}
