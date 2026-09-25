package ir.bita.system;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.core.buffer.Buffer;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.system.support.SystemTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Infrastructure check for every chapter-4 test (answers no research question by itself): the
 * production tools create a service and assign a stored GroovyTemplate to it, a real ESB syncs
 * that config from ESM, and a request to the gateway reaches the provider backend.
 */
class PipelineSmokeTest extends SystemTestBase {

    @Test
    @DisplayName("template + parameters in ESM → real ESB loads it → request reaches the provider")
    void configuredServiceServesTraffic() throws Exception {
        Backend backend = startBackend("smoke-backend");
        GroovyTemplate template = seedRestProxyTemplate();
        int gwPort = freePort();

        Map<String, Object> created = executeTool("create_service", Map.of(
                "serviceName", "smoke-echo",
                "serviceVersion", "v1",
                "collectionName", "smoke"));
        long serviceId = ((Number) created.get("serviceId")).longValue();

        executeTool("service_groovy_config", Map.of(
                "serviceId", serviceId,
                "groovyTemplateId", template.getId(),
                "variableValues", Map.of(
                        "pvAddress", backend.address(),
                        "gwPort", String.valueOf(gwPort),
                        "gwPath", "/esb/smoke/smoke-echo/v1")));

        String apiKey = grantApiKeyConsumer(serviceId, "smoke-consumer");
        startEsb(serviceId);

        Vertx client = Vertx.vertx();
        try {
            HttpResponse<Buffer> resp = WebClient.create(client)
                    .get(gwPort, "127.0.0.1", "/hello")
                    .putHeader("X-API-Key", apiKey)
                    .send()
                    .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertThat(resp.statusCode()).isEqualTo(200);
            JsonObject body = resp.bodyAsJsonObject();
            assertThat(body.getString("marker")).isEqualTo("smoke-backend");
            assertThat(body.getString("path")).isEqualTo("/hello");
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }
}
