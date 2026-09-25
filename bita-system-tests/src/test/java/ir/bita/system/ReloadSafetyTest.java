package ir.bita.system;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import ir.bita.esb.EsbCoreApplication;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.system.support.SystemTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Infrastructure check for the live-apply tests (R1/R2): the ESB's real /internal/reload endpoint
 * swaps the running Groovy script for the new one, and a script that fails to start never takes
 * the running one down.
 */
class ReloadSafetyTest extends SystemTestBase {

    private Vertx client;
    private WebClient http;

    @BeforeEach
    void client() {
        client = Vertx.vertx();
        http = WebClient.create(client);
    }

    @AfterEach
    void closeClient() throws Exception {
        client.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("reload applies the new parameters: the next request reaches the new backend")
    void reloadSwitchesToNewBackend() throws Exception {
        Backend first = startBackend("backend-1");
        Backend second = startBackend("backend-2");
        GroovyTemplate template = seedRestProxyTemplate();
        int gwPort = freePort();
        long serviceId = createService("reload-switch");

        configure(serviceId, template.getId(), first, gwPort);
        EsbCoreApplication.Running esb = startEsb(serviceId);
        assertThat(markerAt(gwPort)).isEqualTo("backend-1");

        configure(serviceId, template.getId(), second, gwPort);
        assertThat(reload(esb).statusCode()).isEqualTo(200);

        assertThat(markerAt(gwPort)).isEqualTo("backend-2");
        for (int i = 0; i < 20; i++) {
            // the old script's Vert.x server must be gone, not sharing the port round-robin
            assertThat(markerAt(gwPort)).isEqualTo("backend-2");
        }
    }

    @Test
    @DisplayName("a template that fails to start is rejected and the running service keeps serving")
    void brokenScriptKeepsRunningService() throws Exception {
        Backend backend = startBackend("healthy");
        GroovyTemplate good = seedRestProxyTemplate();
        GroovyTemplate broken = seedTemplate("broken-runtime", "throws while building the route", List.of());
        int gwPort = freePort();
        long serviceId = createService("reload-broken");

        configure(serviceId, good.getId(), backend, gwPort);
        EsbCoreApplication.Running esb = startEsb(serviceId);
        assertThat(markerAt(gwPort)).isEqualTo("healthy");

        executeTool("service_groovy_config", Map.of(
                "serviceId", serviceId,
                "groovyTemplateId", broken.getId(),
                "variableValues", Map.of()));
        assertThat(reload(esb).statusCode()).isEqualTo(500);

        for (int i = 0; i < 20; i++) {
            assertThat(markerAt(gwPort)).isEqualTo("healthy");
        }
    }

    /** Key of the consumer granted access to the service this test created. */
    private String apiKey;

    private long createService(String name) {
        Map<String, Object> created = executeTool("create_service", Map.of(
                "serviceName", name, "serviceVersion", "v1", "collectionName", name));
        long serviceId = ((Number) created.get("serviceId")).longValue();
        apiKey = grantApiKeyConsumer(serviceId, "consumer-" + name);
        return serviceId;
    }

    private void configure(long serviceId, long templateId, Backend backend, int gwPort) {
        executeTool("service_groovy_config", Map.of(
                "serviceId", serviceId,
                "groovyTemplateId", templateId,
                "variableValues", Map.of(
                        "pvAddress", backend.address(),
                        "gwPort", String.valueOf(gwPort),
                        "gwPath", "/esb/test/v1")));
    }

    private HttpResponse<Buffer> reload(EsbCoreApplication.Running esb) throws Exception {
        return http.post(esb.config().getHttpPort(), "127.0.0.1", "/internal/reload")
                .send()
                .toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private String markerAt(int gwPort) throws Exception {
        HttpResponse<Buffer> resp = http.get(gwPort, "127.0.0.1", "/ping")
                .putHeader("X-API-Key", apiKey)
                .send()
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThat(resp.statusCode()).as("gateway response: %s", resp.bodyAsString()).isEqualTo(200);
        return resp.bodyAsJsonObject().getString("marker");
    }
}
