package ir.bita.esb.route;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import ir.bita.esb.access.RouteAccessService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REST-to-REST proxy integration test using pure Vert.x non-blocking I/O.
 *
 * <pre>
 *   Test client (Vert.x WebClient)
 *       → Gateway (Vert.x HTTP server from Groovy script)
 *           → Backend (Vert.x HTTP server, JSON echo)
 * </pre>
 *
 * Groovy route loaded from {@code rest-proxy-gw-route.groovy}, mirroring how ESB fetches
 * the assembled script from ESM. Vert.x owns all HTTP I/O.
 */
@DisplayName("REST-to-REST proxy via Vert.x non-blocking I/O")
@ExtendWith(VertxExtension.class)
class RestToRestVertxProxyIntegrationTest {

    private static final int BACKEND_PORT = 29_450;
    private static final int GATEWAY_PORT = 29_451;

    private static final String VALID_API_KEY   = "test-key-001";
    private static final String VALID_CLIENT_ID = "client-001";
    private static final int    RATE_LIMIT      = 3;

    private HttpServer          backendServer;
    private DefaultCamelContext camelContext;
    private WebClient           testClient;

    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
        requestCounts.clear();
        testClient = WebClient.create(vertx);

        // ── 1. BACKEND: Vert.x JSON echo server ─────────────────────────────────────────────────
        Router backendRouter = Router.router(vertx);
        backendRouter.route().handler(BodyHandler.create());
        backendRouter.route("/*").handler(rc -> {
            String body = rc.body().asString();
            JsonObject response = new JsonObject()
                    .put("echo", body)
                    .put("path", rc.request().path())
                    .put("clientId", rc.request().getHeader("X-Client-Id"));
            rc.response()
              .putHeader("Content-Type", "application/json")
              .end(response.encode());
        });

        backendServer = vertx.createHttpServer()
                             .requestHandler(backendRouter)
                             .listen(BACKEND_PORT)
                             .toCompletionStage()
                             .toCompletableFuture()
                             .get(5, TimeUnit.SECONDS);

        // ── 2. GATEWAY: load Groovy, inject Vert.x + accessService bindings ─────────────────────
        String script = new String(
                getClass().getResourceAsStream("/rest-proxy-gw-route.groovy").readAllBytes(),
                StandardCharsets.UTF_8);

        RouteAccessService accessService = new RouteAccessService() {
            public String getBitaKeyStore()                { return ""; }
            public String getBitaPassword()               { return ""; }
            public String getClientTrustStore()           { return ""; }
            public List<String> getClientKeys()           { return List.of(); }
            public String getGatewayAddress()             { return "http://127.0.0.1:" + GATEWAY_PORT; }
            public String getProviderTrustStore()         { return ""; }
            public String getProviderAlias()              { return ""; }
            public boolean hasAccess(String clientId)     { return true; }
            public String getClientIdByApiKey(String key) {
                return VALID_API_KEY.equals(key) ? VALID_CLIENT_ID : null;
            }
            public String getClientIdByIp(String ip)     { return null; }
            public boolean checkRateLimit(String clientId) {
                int count = requestCounts
                        .computeIfAbsent(clientId, k -> new AtomicInteger(0))
                        .incrementAndGet();
                return count <= RATE_LIMIT;
            }
        };

        Binding binding = new Binding();
        binding.setVariable("vertxInstance", vertx);
        binding.setVariable("pvAddress",     "http://127.0.0.1:" + BACKEND_PORT + "/api");
        binding.setVariable("gwPort",        GATEWAY_PORT);
        binding.setVariable("accessService", accessService);

        camelContext = new DefaultCamelContext();
        RouteBuilder rb = (RouteBuilder) new GroovyShell(
                Thread.currentThread().getContextClassLoader(), binding).evaluate(script);
        rb.setCamelContext(camelContext);
        camelContext.addRoutes(rb);
        camelContext.start();

        Thread.sleep(500);
        ctx.completeNow();
    }

    @AfterEach
    void tearDown(VertxTestContext ctx) throws Exception {
        if (camelContext != null) camelContext.stop();
        if (backendServer != null) {
            backendServer.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        ctx.completeNow();
    }

    @Test
    @DisplayName("T1: POST with valid API key proxied to backend, client-id forwarded")
    void shouldProxyRequestToBackendWithClientId(Vertx vertx, VertxTestContext ctx) {
        testClient.post(GATEWAY_PORT, "127.0.0.1", "/api/echo")
                  .putHeader("Content-Type", "application/json")
                  .putHeader("X-API-Key", VALID_API_KEY)
                  .sendJson(new JsonObject().put("msg", "hello"))
                  .onSuccess(resp -> ctx.verify(() -> {
                      assertThat(resp.statusCode()).isEqualTo(200);
                      JsonObject body = resp.bodyAsJsonObject();
                      assertThat(body.getString("clientId")).isEqualTo(VALID_CLIENT_ID);
                      assertThat(body.getString("path")).isEqualTo("/api/echo");
                      ctx.completeNow();
                  }))
                  .onFailure(ctx::failNow);
    }

    @Test
    @DisplayName("T2: unknown API key returns 401")
    void shouldRejectUnknownApiKey(Vertx vertx, VertxTestContext ctx) {
        testClient.post(GATEWAY_PORT, "127.0.0.1", "/api/echo")
                  .putHeader("X-API-Key", "bad-key")
                  .send()
                  .onSuccess(resp -> ctx.verify(() -> {
                      assertThat(resp.statusCode()).isEqualTo(401);
                      ctx.completeNow();
                  }))
                  .onFailure(ctx::failNow);
    }

    @Test
    @DisplayName("T3: rate limit enforced — 4th request from same client returns 429")
    void shouldEnforceRateLimit(Vertx vertx, VertxTestContext ctx) throws InterruptedException {
        // Fire first 3 requests (allowed) without waiting
        for (int i = 0; i < RATE_LIMIT; i++) {
            testClient.post(GATEWAY_PORT, "127.0.0.1", "/api/echo")
                      .putHeader("X-API-Key", VALID_API_KEY)
                      .send();
        }
        // Give rate-limit counter time to increment before 4th
        Thread.sleep(100);
        testClient.post(GATEWAY_PORT, "127.0.0.1", "/api/echo")
                  .putHeader("X-API-Key", VALID_API_KEY)
                  .send()
                  .onSuccess(resp -> ctx.verify(() -> {
                      assertThat(resp.statusCode()).isEqualTo(429);
                      ctx.completeNow();
                  }))
                  .onFailure(ctx::failNow);
    }

    @Test
    @DisplayName("T4: request without API key forwarded as anonymous, proxied successfully")
    void shouldAllowAnonymousRequest(Vertx vertx, VertxTestContext ctx) {
        testClient.post(GATEWAY_PORT, "127.0.0.1", "/api/health")
                  .sendJson(new JsonObject().put("ping", "pong"))
                  .onSuccess(resp -> ctx.verify(() -> {
                      assertThat(resp.statusCode()).isEqualTo(200);
                      JsonObject body = resp.bodyAsJsonObject();
                      // No X-Client-Id header forwarded for anonymous requests
                      assertThat(body.getString("clientId")).isNull();
                      ctx.completeNow();
                  }))
                  .onFailure(ctx::failNow);
    }
}
