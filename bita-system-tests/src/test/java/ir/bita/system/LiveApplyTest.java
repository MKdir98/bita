package ir.bita.system;

import ir.bita.system.support.BenchmarkBase;
import ir.bita.system.support.TrafficProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R1 — research question 3 (apply): is an approved configuration change applied to the running
 * ESB without a restart, within the threshold, and without affecting another service?
 *
 * <p>Setup: two services defined from benchmark documents with the LLM supplying their
 * parameters (c01, REST; c03, SOAP), each on its own real ESB, each under continuous consumer
 * traffic. Action: 10 approved changes to the first service's provider address (alternating
 * between two backends) through the production service_groovy_config tool — each one a new
 * minor version that ESM tells the running ESB to reload. Expectation: every change is visible
 * to the consumer with median ≤ 500 ms and P95 ≤ 1000 ms; not one consumer call of either
 * service fails at any point.
 */
@DisplayName("R1 [سؤال ۳] اعمال زندهٔ تغییر تأییدشده، بدون ری‌استارت و بدون اثر بر سرویس دیگر")
class LiveApplyTest extends BenchmarkBase {

    private static final int CHANGES = 10;

    @Test
    void approvedChangesApplyLiveWithoutFailures() throws Exception {
        DefinedService subject = defineService("c01", freePort(), "backend-A");
        startEsb(subject.serviceId());
        Backend backendB = startBackend("backend-B");
        String addressA = String.valueOf(subject.variables().get("pvAddress"));
        String addressB = addressA.replaceFirst("://[^/]+", "://127.0.0.1:" + backendB.port());

        DefinedService neighbour = defineService("c03", freePort(), "neighbour");
        startEsb(neighbour.serviceId());

        List<Long> applyMs = new ArrayList<>();
        try (TrafficProbe consumer = new TrafficProbe(subject.gwPort(), subject.gwPath() + "/ping", subject.apiKey());
             TrafficProbe neighbourConsumer = new TrafficProbe(neighbour.gwPort(), neighbour.gwPath(), true, neighbour.apiKey())) {
            consumer.awaitMarker("backend-A", 0, 10_000);
            neighbourConsumer.awaitMarker("neighbour", 0, 10_000);

            for (int i = 0; i < CHANGES; i++) {
                boolean toB = i % 2 == 0;
                Map<String, Object> vars = new HashMap<>(subject.variables());
                vars.put("pvAddress", toB ? addressB : addressA);
                long t0 = System.currentTimeMillis();
                executeTool("service_groovy_config", Map.of(
                        "serviceId", subject.serviceId(),
                        "groovyTemplateId", configOfService(subject.serviceId()),
                        "variableValues", vars));
                long seen = consumer.awaitMarker(toB ? "backend-B" : "backend-A", t0, 10_000);
                applyMs.add(seen - t0);
            }
            Thread.sleep(500);

            List<TrafficProbe.Sample> failed = consumer.samples().stream().filter(s -> !s.ok()).toList();
            List<TrafficProbe.Sample> neighbourFailed = neighbourConsumer.samples().stream().filter(s -> !s.ok()).toList();
            long median = percentile(applyMs, 50);
            long p95 = percentile(applyMs, 95);

            report("R1", subject, mapOf(
                    "llmSource", subject.source() + " / " + neighbour.source(),
                    "applyMs", applyMs, "medianMs", median, "p95Ms", p95,
                    "consumerCalls", consumer.samples().size(), "consumerFailures", failed.size(),
                    "neighbourCalls", neighbourConsumer.samples().size(), "neighbourFailures", neighbourFailed.size()));

            assertThat(failed).as("consumer calls to the changed service that failed").isEmpty();
            assertThat(neighbourFailed).as("consumer calls to the other service that failed").isEmpty();
            assertThat(median).as("median apply time (ms), all: %s", applyMs).isLessThanOrEqualTo(500);
            assertThat(p95).as("P95 apply time (ms), all: %s", applyMs).isLessThanOrEqualTo(1000);
        }
    }

    private long configOfService(long serviceId) {
        return configRepository.findByServiceId(serviceId).orElseThrow().getGroovyTemplate().getId();
    }

    static long percentile(List<Long> values, int p) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compare);
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
