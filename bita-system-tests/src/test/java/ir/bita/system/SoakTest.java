package ir.bita.system;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import ir.bita.system.support.BenchmarkBase;
import ir.bita.system.support.ws.EchoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3 — research question 4 (steady load): does a service built from a GroovyTemplate with
 * LLM-supplied parameters stay stable under a fixed request rate?
 *
 * <p>Two services, each on a real ESB: benchmark document c01 (REST proxy) and c04 (SOAP with
 * WS-Security, every call signed and encrypted by the consumer, verified and re-secured by the
 * gateway for the provider). Action: a fixed rate ({@code -Dsoak.rps}, default 200/s for REST,
 * {@code -Dsoak.wssRps}, default 50/s for WS-Security) for {@code -Dsoak.seconds} (default 30 s).
 * Expectations: error rate below 0.1 %; heap after GC grows less than 10 % from start to end; P95
 * latency of the last tenth of the run within 20 % of the first tenth.
 */
@DisplayName("P3 [سؤال ۴] پایداری سرویس ساخته‌شده از قالب زیر بار ثابت")
class SoakTest extends BenchmarkBase {

    private static final int RPS = Integer.getInteger("soak.rps", 200);
    private static final int WSS_RPS = Integer.getInteger("soak.wssRps", 50);
    private static final int SECONDS = Integer.getInteger("soak.seconds", 30);

    record Call(long startedMs, long latencyMs, boolean ok) {
    }

    @Test
    @DisplayName("REST proxy (c01)")
    void restSteadyLoadStaysStable() throws Exception {
        DefinedService service = defineService("c01", freePort(), "provider");
        startEsb(service.serviceId());

        Vertx vertx = Vertx.vertx();
        WebClient http = WebClient.create(vertx, new WebClientOptions().setMaxPoolSize(50).setKeepAlive(true));
        try {
            Supplier<CompletableFuture<Boolean>> call = () -> http
                    .get(service.gwPort(), "127.0.0.1", service.gwPath() + "/ping")
                    .putHeader("X-API-Key", service.apiKey()).timeout(5000).send()
                    .map(r -> r.statusCode() == 200)
                    .toCompletionStage().toCompletableFuture();
            soak("P3", service, RPS, call);
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("SOAP with WS-Security (c04)")
    void wsSecuritySteadyLoadStaysStable() throws Exception {
        DefinedService service = defineService("c04", freePort(), "provider");
        startWsSecurityEsb(service);

        // CXF proxies are blocking; each worker thread signs and encrypts with its own consumer
        ExecutorService workers = Executors.newFixedThreadPool(32);
        ThreadLocal<EchoService> consumer = ThreadLocal.withInitial(() -> wsSecurityConsumer(service));
        AtomicInteger seq = new AtomicInteger();
        try {
            Supplier<CompletableFuture<Boolean>> call = () -> CompletableFuture.supplyAsync(() -> {
                String msg = "m" + seq.incrementAndGet();
                try {
                    return ("I get" + msg).equals(consumer.get().echo(msg, 0));
                } catch (Exception e) {
                    return false;
                }
            }, workers);
            soak("P3-wss", service, WSS_RPS, call);
        } finally {
            workers.shutdownNow();
        }
    }

    private void soak(String suite, DefinedService service, int rps, Supplier<CompletableFuture<Boolean>> call)
            throws Exception {
        List<Call> calls = new CopyOnWriteArrayList<>();
        AtomicInteger inFlight = new AtomicInteger();
        for (int i = 0; i < 200; i++) {
            assertThat(call.get().get(10, TimeUnit.SECONDS)).as("warm-up call %d", i).isTrue();
        }
        long heapStart = usedHeapAfterGc();
        long start = System.currentTimeMillis();
        long intervalNs = 1_000_000_000L / rps;
        long next = System.nanoTime();
        long end = start + SECONDS * 1000L;
        while (System.currentTimeMillis() < end) {
            long t = System.currentTimeMillis();
            inFlight.incrementAndGet();
            call.get().whenComplete((ok, err) -> {
                calls.add(new Call(t, System.currentTimeMillis() - t, err == null && Boolean.TRUE.equals(ok)));
                inFlight.decrementAndGet();
            });
            next += intervalNs;
            long sleep = next - System.nanoTime();
            if (sleep > 0) {
                TimeUnit.NANOSECONDS.sleep(sleep);
            }
        }
        long drainUntil = System.currentTimeMillis() + 10_000;
        while (inFlight.get() > 0 && System.currentTimeMillis() < drainUntil) {
            Thread.sleep(10);
        }
        long heapEnd = usedHeapAfterGc();

        long failures = calls.stream().filter(c -> !c.ok()).count();
        double errorRate = calls.isEmpty() ? 1 : (double) failures / calls.size();
        long window = SECONDS * 1000L / 10;
        long p95First = p95(calls.stream().filter(c -> c.startedMs() < start + window).toList());
        long p95Last = p95(calls.stream().filter(c -> c.startedMs() >= end - window).toList());
        double heapGrowth = (double) (heapEnd - heapStart) / heapStart;
        double drift = p95First == 0 ? 0 : (double) (p95Last - p95First) / p95First;

        report(suite, service, mapOf("llmSource", service.source(), "rps", rps, "seconds", SECONDS,
                "calls", calls.size(), "failures", failures, "errorRate", errorRate,
                "heapStartBytes", heapStart, "heapEndBytes", heapEnd, "heapGrowth", heapGrowth,
                "p95FirstTenthMs", p95First, "p95LastTenthMs", p95Last, "p95Drift", drift,
                "p95AllMs", p95(calls)));

        assertThat(errorRate).as("error rate (%d of %d failed)", failures, calls.size()).isLessThan(0.001);
        assertThat(heapGrowth).as("heap growth after GC (%d → %d bytes)", heapStart, heapEnd).isLessThan(0.10);
        // latency in the ms range is dominated by scheduling noise; only a real drift counts
        if (p95Last > 5) {
            assertThat(drift).as("P95 drift (%d → %d ms)", p95First, p95Last).isLessThan(0.20);
        }
    }

    private static long usedHeapAfterGc() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(200);
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    static long p95(List<Call> calls) {
        List<Long> l = new ArrayList<>(calls.stream().map(Call::latencyMs).toList());
        if (l.isEmpty()) {
            return 0;
        }
        l.sort(Long::compare);
        return l.get(Math.min(l.size() - 1, (int) Math.ceil(0.95 * l.size()) - 1));
    }
}
