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
 * R2 — research question 3 (rollback): when an approved minor version turns out to be faulty,
 * does rolling back to the last good one (e.g. 1.7 → 1.6) restore the service on the same
 * address without an outage?
 *
 * <p>Setup: a service defined from benchmark document c01 with the LLM supplying its
 * parameters (version x.1, healthy), on a real ESB, under continuous consumer traffic. Action,
 * 5 times: approve a faulty version (provider address pointing where nothing listens — the
 * service still runs but every call fails, which is the bug), wait until consumers see it, then
 * roll back through the production rollback_service_config tool. Expectation: the healthy
 * version answers again within the apply threshold; from the rollback on, no consumer call
 * fails for any reason other than the faulty version itself (no connection loss, no gap); and
 * after the rollback completes, every call succeeds.
 */
@DisplayName("R2 [سؤال ۳] بازگشت نسخهٔ فرعی معیوب به نسخهٔ سالم، روی همان آدرس و بدون قطعی")
class RollbackTest extends BenchmarkBase {

    private static final int ROUNDS = 5;

    @Test
    void faultyVersionRollsBackWithoutOutage() throws Exception {
        DefinedService service = defineService("c01", freePort(), "healthy");
        startEsb(service.serviceId());
        String goodVersion = activeVersion(service.serviceId());
        String deadAddress = String.valueOf(service.variables().get("pvAddress"))
                .replaceFirst("://[^/]+", "://127.0.0.1:" + freePort());

        List<Long> rollbackMs = new ArrayList<>();
        List<String> faultyVersions = new ArrayList<>();
        List<TrafficProbe.Sample> unexpected = new ArrayList<>();
        List<TrafficProbe.Sample> afterRecovery = new ArrayList<>();

        try (TrafficProbe consumer = new TrafficProbe(service.gwPort(), service.gwPath() + "/ping", service.apiKey())) {
            consumer.awaitMarker("healthy", 0, 10_000);

            for (int i = 0; i < ROUNDS; i++) {
                Map<String, Object> faulty = new HashMap<>(service.variables());
                faulty.put("pvAddress", deadAddress);
                long approved = System.currentTimeMillis();
                Map<String, Object> result = executeTool("service_groovy_config", Map.of(
                        "serviceId", service.serviceId(),
                        "groovyTemplateId", configRepository.findByServiceId(service.serviceId()).orElseThrow()
                                .getGroovyTemplate().getId(),
                        "variableValues", faulty));
                faultyVersions.add(String.valueOf(result.get("version")));
                awaitFailure(consumer, approved);

                long t0 = System.currentTimeMillis();
                executeTool("rollback_service_config", Map.of("serviceId", service.serviceId(), "version", goodVersion));
                long recovered = consumer.awaitMarker("healthy", t0, 10_000);
                rollbackMs.add(recovered - t0);

                // from the rollback on, the only acceptable failure is the faulty version's own 502
                consumer.since(t0).stream()
                        .filter(s -> !s.ok() && s.status() != 502)
                        .forEach(unexpected::add);
                Thread.sleep(300);
                consumer.since(recovered + 50).stream().filter(s -> !s.ok()).forEach(afterRecovery::add);
            }

            report("R2", service, mapOf(
                    "llmSource", service.source(),
                    "goodVersion", goodVersion, "faultyVersions", faultyVersions,
                    "rollbackMs", rollbackMs,
                    "medianMs", LiveApplyTest.percentile(rollbackMs, 50),
                    "p95Ms", LiveApplyTest.percentile(rollbackMs, 95),
                    "unexpectedFailures", unexpected.size(), "failuresAfterRecovery", afterRecovery.size(),
                    "activeVersionAtEnd", activeVersion(service.serviceId())));

            assertThat(activeVersion(service.serviceId())).isEqualTo(goodVersion);
            assertThat(unexpected).as("calls that failed for a reason other than the faulty version").isEmpty();
            assertThat(afterRecovery).as("calls that failed after the rollback completed").isEmpty();
            assertThat(LiveApplyTest.percentile(rollbackMs, 50)).as("median rollback time (ms): %s", rollbackMs)
                    .isLessThanOrEqualTo(500);
            assertThat(LiveApplyTest.percentile(rollbackMs, 95)).as("P95 rollback time (ms): %s", rollbackMs)
                    .isLessThanOrEqualTo(1000);
        }
    }

    private String activeVersion(long serviceId) {
        return versionService.activeLabel(serviceId).orElseThrow();
    }

    private static void awaitFailure(TrafficProbe consumer, long fromMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (consumer.since(fromMs).stream().anyMatch(s -> !s.ok())) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("the faulty version never reached consumers");
    }
}
