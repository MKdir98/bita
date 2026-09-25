package ir.bita.system;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.service.command.RevokeAccessCommand;
import ir.bita.esm.service.handler.RevokeAccessHandler;
import ir.bita.system.support.SystemTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security — consumer access control on a running service.
 *
 * <p>Setup: a rest-proxy service brought up from its GroovyTemplate through ESM's tools on a real
 * ESB, and a consumer organisation registered in ESM with an API key and granted access through
 * the handler the ESM API uses. Action: call the gateway as that consumer, with no key, with a key
 * ESM never issued, and — after revoking the grant through ESM — as the same consumer again.
 * Expectation: only the granted consumer is served, and the provider learns who it is
 * (X-Client-Id) but never sees the consumer's key; every other call is refused at the gateway
 * (401 without or with an unknown key, 403 once revoked) and never reaches the provider. The
 * revocation takes effect on the running ESB without a restart.
 */
@DisplayName("امنیت: کنترل دسترسی مصرف‌کننده روی سرویس در حال اجرا")
class AccessControlTest extends SystemTestBase {

    @Autowired
    RevokeAccessHandler revokeAccessHandler;

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
    void onlyGrantedConsumersReachTheProvider() throws Exception {
        Backend provider = startBackend("guarded");
        GroovyTemplate template = seedRestProxyTemplate();
        int gwPort = freePort();

        Map<String, Object> created = executeTool("create_service", Map.of(
                "serviceName", "guarded", "serviceVersion", "v1", "collectionName", "access-control"));
        long serviceId = ((Number) created.get("serviceId")).longValue();
        executeTool("service_groovy_config", Map.of(
                "serviceId", serviceId, "groovyTemplateId", template.getId(),
                "variableValues", new HashMap<>(Map.of(
                        "pvAddress", provider.address(),
                        "gwPort", String.valueOf(gwPort), "gwPath", "/esb/guarded/v1"))));
        ApiKeyConsumer consumer = grantApiKeyConsumerWithKey(serviceId, "granted-org", "granted-key-1");
        startEsb(serviceId);

        HttpResponse<Buffer> granted = call(gwPort, consumer.apiKey());
        assertThat(granted.statusCode()).as(granted.bodyAsString()).isEqualTo(200);
        assertThat(granted.bodyAsJsonObject().getString("marker")).isEqualTo("guarded");
        assertThat(granted.bodyAsJsonObject().getString("clientId"))
                .as("the provider is told which consumer called").isEqualTo(String.valueOf(consumer.clientId()));
        assertThat(granted.bodyAsJsonObject().getString("apiKeySeen"))
                .as("the consumer's key must not be passed on to the provider").isNull();
        int reachedAfterGranted = provider.calls().get();

        HttpResponse<Buffer> noKey = call(gwPort, null);
        assertThat(noKey.statusCode()).isEqualTo(401);
        assertThat(noKey.bodyAsString()).isEqualTo("API key required");

        HttpResponse<Buffer> unknownKey = call(gwPort, "never-issued");
        assertThat(unknownKey.statusCode()).isEqualTo(401);
        assertThat(unknownKey.bodyAsString()).isEqualTo("Unknown API key");
        assertThat(provider.calls().get()).as("refused calls must not reach the provider").isEqualTo(reachedAfterGranted);

        long revokedAt = System.currentTimeMillis();
        revokeAccessHandler.handle(RevokeAccessCommand.builder()
                .accessId(consumer.accessId()).reason("system test: access withdrawn").build());
        HttpResponse<Buffer> revoked = awaitStatus(gwPort, consumer.apiKey(), 403, 10_000);
        long revokeEffectiveMs = System.currentTimeMillis() - revokedAt;
        assertThat(revoked.statusCode()).as("after revoke: %s", revoked.bodyAsString()).isEqualTo(403);
        int reachedAfterRevoke = provider.calls().get();
        for (int i = 0; i < 5; i++) {
            assertThat(call(gwPort, consumer.apiKey()).statusCode()).isEqualTo(403);
        }
        assertThat(provider.calls().get()).as("a revoked consumer must not reach the provider").isEqualTo(reachedAfterRevoke);

        System.out.printf("[ACCESS] granted=200 noKey=%d unknownKey=%d revoked=%d revokeEffectiveMs=%d%n",
                noKey.statusCode(), unknownKey.statusCode(), revoked.statusCode(), revokeEffectiveMs);
    }

    private HttpResponse<Buffer> call(int gwPort, String apiKey) throws Exception {
        var req = http.get(gwPort, "127.0.0.1", "/ping").timeout(5000);
        if (apiKey != null) {
            req.putHeader("X-API-Key", apiKey);
        }
        return req.send().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /** Polls until the gateway answers {@code status} (the revoke reaches the ESB asynchronously). */
    private HttpResponse<Buffer> awaitStatus(int gwPort, String apiKey, int status, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        HttpResponse<Buffer> last;
        do {
            last = call(gwPort, apiKey);
            if (last.statusCode() == status) {
                return last;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        return last;
    }
}
