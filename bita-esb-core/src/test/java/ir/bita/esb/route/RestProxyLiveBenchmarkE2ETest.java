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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof for one real LLM-benchmark case: not a mock of the ESB tool layer (as
 * llm-benchmark/src/run.ts uses for scoring all 50 cases), but the actual production
 * Groovy route ({@code rest-proxy-gw-route.groovy}) executed by real Camel + Vert.x,
 * bound with the EXACT {@code variableValues} a live LLM run produced, proxying a real
 * HTTP request to a freshly started random backend.
 *
 * <p>Provenance of the case (see llm-benchmark/data/cases/c_live01.json and
 * llm-benchmark/results/2026-09-25T12-54-41_auto_esm+tools/transcripts/c_live01-r1.json):
 * the LLM ("auto" route, answered by google/gemma-4-26b-a4b-it:free) was given a Persian
 * service-request document naming a REAL, reachable backend address
 * (http://127.0.0.1:29500/api — a fake address like the benchmark's synthetic cases use
 * cannot be dialled from this machine) and produced this {@code service_groovy_config}
 * tool call verbatim:
 * <pre>
 *   {"serviceId": null, "groovyTemplateId": 101,
 *    "variableValues": {"pvAddress": "http://127.0.0.1:29500/api",
 *                        "gwPort": "29501", "gwPath": "/esb/demo/echo-proxy/v1"}}
 * </pre>
 * {@code GroovyBenchmarkH2Test} (bita-esm-backend) persists that same call on a real H2
 * database and re-derives {@code assembledScript}; this test takes {@code pvAddress} and
 * {@code gwPort} from it and actually runs them.
 */
@DisplayName("Live benchmark case c_live01, executed for real (not scored offline)")
@ExtendWith(VertxExtension.class)
class RestProxyLiveBenchmarkE2ETest {

    // == exactly what the LLM's own service_groovy_config call contained ==
    private static final String PV_ADDRESS = "http://127.0.0.1:29500/api";
    private static final int GW_PORT = 29501;

    private HttpServer backendServer;
    private DefaultCamelContext camelContext;
    private WebClient testClient;
    private String backendMarker;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
        testClient = WebClient.create(vertx);
        // a fresh random value per run, so a passing assertion can only mean this run's
        // backend instance was actually reached through the gateway — not a cached/stale one
        backendMarker = UUID.randomUUID().toString();

        // ── 1. BACKEND: a real, freshly bound HTTP server on the port the LLM was told about ──
        int backendPort = Integer.parseInt(PV_ADDRESS.split(":")[2].split("/")[0]);
        Router backendRouter = Router.router(vertx);
        backendRouter.route().handler(BodyHandler.create());
        backendRouter.route("/*").handler(rc -> rc.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                        .put("marker", backendMarker)
                        .put("path", rc.request().path())
                        .encode()));
        backendServer = vertx.createHttpServer()
                             .requestHandler(backendRouter)
                             .listen(backendPort)
                             .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        // ── 2. GATEWAY: the real production Groovy script, bound with the LLM's own values ──
        String script = new String(
                getClass().getResourceAsStream("/rest-proxy-gw-route.groovy").readAllBytes(),
                StandardCharsets.UTF_8);

        RouteAccessService accessService = new RouteAccessService() {
            public String getBitaKeyStore()                { return ""; }
            public String getBitaPassword()                { return ""; }
            public String getClientTrustStore()             { return ""; }
            public List<String> getClientKeys()             { return List.of(); }
            public String getGatewayAddress()               { return "http://127.0.0.1:" + GW_PORT; }
            public String getProviderTrustStore()           { return ""; }
            public String getProviderAlias()                { return ""; }
            public boolean hasAccess(String clientId)       { return true; }
            public String getClientIdByApiKey(String key)   { return null; }
            public String getClientIdByIp(String ip)        { return null; }
            public boolean checkRateLimit(String clientId)  { return true; }
        };

        Binding binding = new Binding();
        binding.setVariable("vertxInstance", vertx);
        binding.setVariable("pvAddress", PV_ADDRESS);   // <- straight from the LLM's tool call
        binding.setVariable("gwPort", GW_PORT);           // <- straight from the LLM's tool call
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
    @DisplayName("request through the LLM-configured gateway reaches the freshly started backend")
    void llmGeneratedConfigActuallyWorks(Vertx vertx, VertxTestContext ctx) {
        testClient.get(GW_PORT, "127.0.0.1", "/echo")
                  .send()
                  .onSuccess(resp -> ctx.verify(() -> {
                      assertThat(resp.statusCode()).isEqualTo(200);
                      JsonObject body = resp.bodyAsJsonObject();
                      // proves the request actually left the gateway process and hit THIS
                      // test run's backend instance, not a cached response or a different port
                      assertThat(body.getString("marker")).isEqualTo(backendMarker);
                      assertThat(body.getString("path")).isEqualTo("/echo");
                      ctx.completeNow();
                  }))
                  .onFailure(ctx::failNow);
    }
}
