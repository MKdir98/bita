package ir.bita.system;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import ir.bita.system.support.BenchmarkBase;
import ir.bita.system.support.ws.EchoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4 — research question 4 (rising load): how does a service built from a GroovyTemplate with
 * LLM-supplied parameters behave as concurrency rises, and does it recover once load is removed?
 *
 * <p>Two services, each on a real ESB: benchmark document c01 (REST proxy) and c04 (SOAP with
 * WS-Security, every call signed and encrypted). Action: closed-loop consumers (each sends its
 * next request as soon as the previous one answers), stepped 1, 2, 4, 8, 16, 32, 64, 128 for
 * {@code -Dcapacity.stepSeconds} (default 5 s) each. Reported per step: throughput, P95 latency,
 * error rate; the observed capacity is the highest step with error rate < 1 % and P95 ≤ 1000 ms.
 * This is descriptive for this hardware — no threshold on the number itself. Expectation: once
 * load is removed, a single call succeeds again.
 */
@DisplayName("P4 [سؤال ۴] رفتار سرویس ساخته‌شده از قالب زیر بار فزاینده (ظرفیت مشاهده‌شده)")
class CapacityTest extends BenchmarkBase {

    private static final int[] STEPS = {1, 2, 4, 8, 16, 32, 64, 128};
    private static final int STEP_SECONDS = Integer.getInteger("capacity.stepSeconds", 5);

    record Call(long latencyMs, boolean ok) {
    }

    @Test
    @DisplayName("REST proxy (c01)")
    void restRisingConcurrencyIsMeasuredAndRecovers() throws Exception {
        DefinedService service = defineService("c01", freePort(), "provider");
        startEsb(service.serviceId());

        Vertx vertx = Vertx.vertx();
        WebClient http = WebClient.create(vertx, new WebClientOptions().setMaxPoolSize(256).setKeepAlive(true));
        try {
            measure("P4", service, () -> http.get(service.gwPort(), "127.0.0.1", service.gwPath() + "/ping")
                    .putHeader("X-API-Key", service.apiKey()).timeout(5000).send()
                    .map(r -> r.statusCode() == 200)
                    .toCompletionStage().toCompletableFuture());
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("SOAP with WS-Security (c04)")
    void wsSecurityRisingConcurrencyIsMeasuredAndRecovers() throws Exception {
        DefinedService service = defineService("c04", freePort(), "provider");
        startWsSecurityEsb(service);

        // one blocking CXF consumer per thread; enough threads for the largest step
        ExecutorService workers = Executors.newFixedThreadPool(STEPS[STEPS.length - 1] + 1);
        ThreadLocal<EchoService> consumer = ThreadLocal.withInitial(() -> wsSecurityConsumer(service));
        AtomicInteger seq = new AtomicInteger();
        try {
            measure("P4-wss", service, () -> CompletableFuture.supplyAsync(() -> {
                String msg = "m" + seq.incrementAndGet();
                try {
                    return ("I get" + msg).equals(consumer.get().echo(msg, 0));
                } catch (Exception e) {
                    return false;
                }
            }, workers));
        } finally {
            workers.shutdownNow();
        }
    }

    private void measure(String suite, DefinedService service, Supplier<CompletableFuture<Boolean>> call)
            throws Exception {
        List<Map<String, Object>> steps = new ArrayList<>();
        Integer capacity = null;
        for (int consumers : STEPS) {
            List<Call> calls = new CopyOnWriteArrayList<>();
            AtomicBoolean running = new AtomicBoolean(true);
            for (int i = 0; i < consumers; i++) {
                loop(call, calls, running);
            }
            Thread.sleep(STEP_SECONDS * 1000L);
            running.set(false);
            Thread.sleep(300);

            long failures = calls.stream().filter(c -> !c.ok()).count();
            double errorRate = calls.isEmpty() ? 1 : (double) failures / calls.size();
            List<Long> lat = new ArrayList<>(calls.stream().map(Call::latencyMs).toList());
            lat.sort(Long::compare);
            long p95 = lat.isEmpty() ? 0 : lat.get(Math.min(lat.size() - 1, (int) Math.ceil(0.95 * lat.size()) - 1));
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("consumers", consumers);
            step.put("calls", calls.size());
            step.put("throughputPerSec", calls.size() / (double) STEP_SECONDS);
            step.put("p95Ms", p95);
            step.put("errorRate", errorRate);
            steps.add(step);
            if (errorRate < 0.01 && p95 <= 1000) {
                capacity = consumers;
            }
        }
        Thread.sleep(2000);
        boolean after = call.get().get(30, TimeUnit.SECONDS);

        report(suite, service, mapOf("llmSource", service.source(), "stepSeconds", STEP_SECONDS,
                "steps", steps, "observedCapacityConsumers", capacity, "servedAfterLoadRemoved", after));

        assertThat(after).as("a call after the load is removed").isTrue();
    }

    private static void loop(Supplier<CompletableFuture<Boolean>> call, List<Call> calls, AtomicBoolean running) {
        if (!running.get()) {
            return;
        }
        long t = System.currentTimeMillis();
        call.get().whenComplete((ok, err) -> {
            calls.add(new Call(System.currentTimeMillis() - t, err == null && Boolean.TRUE.equals(ok)));
            loop(call, calls, running);
        });
    }
}
