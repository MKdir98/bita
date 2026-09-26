package ir.bita.esb.sync;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import ir.bita.esb.access.RouteAccessService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A trial run of a proposed template, before it joins the catalog: the script is compiled with
 * the ESB's own libraries, started exactly as a service's script would be, put in front of a
 * stand-in provider, and called — once without an API key, once as a granted consumer.
 *
 * <p>It fails the trial if the script does not compile (e.g. a class that does not exist), does
 * not start, lets a request through without a key, or throws while handling a request (e.g. a
 * method called with arguments it does not take — in Groovy only a call reveals that). Whatever
 * status the script itself chooses to answer is its business; only a crash or a missing access
 * check fails. Nothing of the trial outlives it: the script, the stand-in provider and the
 * listener are all closed afterwards.
 *
 * <p>The script compiles with {@link ScriptSandbox}'s restrictions, as it will on every gateway
 * that later runs it.
 *
 * <p>A variable value may contain {@value #PROVIDER}; it is replaced with the stand-in provider's
 * address, so the caller need not know where that runs.
 */
@Slf4j
public final class ScriptTrial {

    public static final String PROVIDER = "${TRIAL_PROVIDER}";
    static final String TRIAL_KEY = "trial-key";
    private static final long CALL_TIMEOUT_MS = 10_000;

    private ScriptTrial() {
    }

    /** {"ok": true} or {"ok": false, "stage": compile|start|access|request, "error": ...}. */
    public static Future<JsonObject> run(Vertx vertx, String script, Map<String, Object> variables) {
        AtomicReference<Throwable> crash = new AtomicReference<>();
        AtomicReference<HttpServer> provider = new AtomicReference<>();
        AtomicReference<HttpServer> gateway = new AtomicReference<>();
        AtomicReference<String> deployment = new AtomicReference<>();
        WebClient http = WebClient.create(vertx);

        Future<JsonObject> result = vertx.createHttpServer()
                .requestHandler(req -> req.response().putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("trial", "ok").put("path", req.path()).encode()))
                .listen(0)
                .compose(pv -> {
                    provider.set(pv);
                    Map<String, Object> vars = new HashMap<>();
                    String origin = "http://127.0.0.1:" + pv.actualPort();
                    variables.forEach((k, v) -> vars.put(k, String.valueOf(v).replace(PROVIDER, origin)));
                    return vertx.<Class<? extends Script>>executeBlocking(() ->
                                    new GroovyShell(Thread.currentThread().getContextClassLoader(), ScriptSandbox.configuration())
                                            .parse(script).getClass())
                            .transform(c -> c.succeeded() ? Future.succeededFuture(c.result())
                                    : Future.failedFuture(new Stage("compile", c.cause())))
                            .compose(cls -> {
                                ScriptVerticle verticle = new ScriptVerticle(cls, vars, new TrialAccess());
                                return vertx.deployVerticle(verticle)
                                        .transform(d -> {
                                            if (d.failed()) {
                                                return Future.failedFuture(new Stage("start", d.cause()));
                                            }
                                            deployment.set(d.result());
                                            return Future.succeededFuture(verticle);
                                        });
                            });
                })
                .compose(verticle -> {
                    // a closure that throws inside a callback (onSuccess, a timer) never reaches the
                    // router; the sandbox runs nothing but trials, so catch it on the Vert.x instance
                    vertx.exceptionHandler(t -> crash.compareAndSet(null, t));
                    Router router = verticle.gatewayRouter();
                    // the router answers 500 for a handler that threw; keep what it threw
                    router.errorHandler(500, ctx -> {
                        crash.compareAndSet(null, ctx.failure());
                        ctx.response().setStatusCode(500).end("trial: handler threw");
                    });
                    return vertx.createHttpServer().requestHandler(router).listen(0);
                })
                .compose(gw -> {
                    gateway.set(gw);
                    String path = String.valueOf(variables.getOrDefault("gwPath", "/trial")) + "/trial";
                    return call(http, gw.actualPort(), path, null)
                            .compose(noKey -> {
                                if (crash.get() != null) {
                                    return Future.failedFuture(new Stage("request", crash.get()));
                                }
                                if (noKey.statusCode() != 401 && noKey.statusCode() != 403) {
                                    return Future.failedFuture(new Stage("access", new IllegalStateException(
                                            "a call without X-API-Key was answered " + noKey.statusCode()
                                                    + " instead of 401 — the consumer access check is missing")));
                                }
                                return call(http, gw.actualPort(), path, TRIAL_KEY)
                                        .recover(err -> Future.failedFuture(crash.get() != null ? crash.get()
                                                : err instanceof java.util.concurrent.TimeoutException
                                                ? new IllegalStateException("a granted consumer's GET " + path
                                                + " got no response within " + CALL_TIMEOUT_MS + "ms, although the"
                                                + " stand-in provider answers at once — some path through the"
                                                + " handler never ends the response (every onSuccess/onFailure"
                                                + " branch must call ctx.response().end(...)), or it waits for a"
                                                + " request body that BodyHandler already consumed (use ctx.body())")
                                                : err));
                            })
                            .compose(granted -> crash.get() != null
                                    ? Future.failedFuture(new Stage("request", crash.get()))
                                    : Future.succeededFuture(new JsonObject().put("ok", true)
                                            .put("status", granted.statusCode())));
                })
                .recover(err -> {
                    Stage stage = err instanceof Stage s ? s : new Stage("request", err);
                    Throwable cause = stage.getCause() == null ? stage : stage.getCause();
                    return Future.succeededFuture(new JsonObject().put("ok", false).put("stage", stage.stage)
                            .put("error", cause.getClass().getName() + ": " + String.valueOf(cause.getMessage())));
                });

        return result.eventually(() -> {
            vertx.exceptionHandler(null);
            http.close();
            Future<?> undeploy = deployment.get() == null ? Future.succeededFuture() : vertx.undeploy(deployment.get());
            return undeploy.eventually(() -> Future.join(close(gateway.get()), close(provider.get())));
        });
    }

    private static Future<HttpResponse<Buffer>> call(WebClient http, int port, String path, String apiKey) {
        var req = http.request(HttpMethod.GET, port, "127.0.0.1", path).timeout(CALL_TIMEOUT_MS);
        if (apiKey != null) {
            req.putHeader("X-API-Key", apiKey);
        }
        return req.send();
    }

    private static Future<Void> close(HttpServer server) {
        return server == null ? Future.succeededFuture() : server.close();
    }

    /** Which step failed. */
    private static final class Stage extends RuntimeException {
        final String stage;

        Stage(String stage, Throwable cause) {
            super(cause);
            this.stage = stage;
        }
    }

    /** One granted consumer, {@value #TRIAL_KEY}; nobody else. */
    private static final class TrialAccess implements RouteAccessService {
        @Override public String getBitaKeyStore() { return ""; }
        @Override public String getBitaPassword() { return ""; }
        @Override public String getClientTrustStore() { return ""; }
        @Override public List<String> getClientKeys() { return List.of(); }
        @Override public boolean hasAccess(String clientId) { return "trial-client".equals(clientId); }
        @Override public boolean checkRateLimit(String clientId) { return true; }
        @Override public String getClientIdByApiKey(String apiKey) { return TRIAL_KEY.equals(apiKey) ? "trial-client" : null; }
        @Override public String getClientIdByIp(String ip) { return null; }
        @Override public String getGatewayAddress() { return ""; }
        @Override public String getProviderTrustStore() { return ""; }
        @Override public String getProviderAlias() { return ""; }
    }
}
