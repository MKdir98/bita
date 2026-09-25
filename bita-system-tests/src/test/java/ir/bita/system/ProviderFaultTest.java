package ir.bita.system;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import ir.bita.system.support.BenchmarkBase;
import ir.bita.system.support.ControllableBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — research question 4 (provider faults): does a service built from a GroovyTemplate with
 * LLM-supplied parameters behave predictably when its provider fails, and recover as soon as
 * the provider does?
 *
 * <p>Setup: the service for benchmark document c01 (REST), parameters from the LLM, on a real
 * ESB; its provider is switchable. Actions and expectations:
 * 500 and 429 from the provider are passed to the consumer unchanged; a provider that is down
 * gives the consumer 502 at once (well under the connect timeout of a live connection) rather
 * than a hang; after 10 consecutive 500s, and after the provider heals from 500 / 429 / being
 * down, the very next call succeeds; a mixed sequence gives each call its own outcome.
 */
@DisplayName("P2 [سؤال ۴] رفتار سرویس ساخته‌شده از قالب در برابر خطای ارائه‌دهنده و بازیابی")
class ProviderFaultTest extends BenchmarkBase {

    private ControllableBackend provider;
    private DefinedService service;
    private Vertx client;
    private WebClient http;
    private final Map<String, Object> results = new LinkedHashMap<>();

    @BeforeEach
    void bringUp() throws Exception {
        int pvPort = freePort();
        provider = new ControllableBackend(pvPort, "provider");
        service = defineService("c01", pvPort, "provider", false);
        startEsb(service.serviceId());
        client = Vertx.vertx();
        http = WebClient.create(client);
        assertThat(call().statusCode()).isEqualTo(200);
    }

    @AfterEach
    void tearDown() throws Exception {
        report("P2", service, results);
        client.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        provider.close();
    }

    @Test
    @DisplayName("500 / 429 passed through, down → immediate 502, and the next call after healing succeeds")
    void faultsAreContainedAndRecovered() throws Exception {
        provider.mode(ControllableBackend.Mode.ERROR_500);
        assertThat(call().statusCode()).as("provider 500").isEqualTo(500);

        provider.mode(ControllableBackend.Mode.ERROR_429);
        assertThat(call().statusCode()).as("provider 429").isEqualTo(429);

        provider.mode(ControllableBackend.Mode.OK);
        provider.down();
        long t = System.currentTimeMillis();
        int downStatus = call().statusCode();
        long downMs = System.currentTimeMillis() - t;
        results.put("providerDownStatus", downStatus);
        results.put("providerDownResponseMs", downMs);
        assertThat(downStatus).as("provider down").isEqualTo(502);
        assertThat(downMs).as("time to answer while provider is down (ms)").isLessThan(1000);
        provider.up();
        assertThat(call().statusCode()).as("first call after provider came back").isEqualTo(200);

        provider.mode(ControllableBackend.Mode.ERROR_500);
        for (int i = 0; i < 10; i++) {
            assertThat(call().statusCode()).isEqualTo(500);
        }
        provider.mode(ControllableBackend.Mode.OK);
        assertThat(call().statusCode()).as("first call after 10 consecutive 500s").isEqualTo(200);

        results.put("recoveryAfter500Ms", recoveryAfter(ControllableBackend.Mode.ERROR_500));
        results.put("recoveryAfter429Ms", recoveryAfter(ControllableBackend.Mode.ERROR_429));

        List<Integer> mixed = new ArrayList<>();
        ControllableBackend.Mode[] sequence = {ControllableBackend.Mode.OK, ControllableBackend.Mode.ERROR_500,
                ControllableBackend.Mode.OK, ControllableBackend.Mode.ERROR_429, ControllableBackend.Mode.OK};
        for (ControllableBackend.Mode m : sequence) {
            provider.mode(m);
            mixed.add(call().statusCode());
        }
        results.put("mixedSequence", mixed);
        assertThat(mixed).containsExactly(200, 500, 200, 429, 200);
    }

    /** Provider fails, then heals; how long until the consumer's next call is served (ms). */
    private long recoveryAfter(ControllableBackend.Mode fault) throws Exception {
        provider.mode(fault);
        assertThat(call().statusCode()).isNotEqualTo(200);
        provider.mode(ControllableBackend.Mode.OK);
        long t = System.currentTimeMillis();
        int status = call().statusCode();
        long ms = System.currentTimeMillis() - t;
        assertThat(status).as("first call after healing from %s", fault).isEqualTo(200);
        return ms;
    }

    private HttpResponse<Buffer> call() throws Exception {
        return http.get(service.gwPort(), "127.0.0.1", service.gwPath() + "/ping").putHeader("X-API-Key", service.apiKey()).timeout(10_000).send()
                .toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }
}
