package ir.bita.system;

import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.entity.ServiceGroovyConfig;
import ir.bita.system.support.BenchmarkBase;
import ir.bita.system.support.BenchmarkCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B1 — research question 1: given a natural-language service request, does the LLM pick the
 * matching GroovyTemplate and extract its parameters correctly, so that the resulting service
 * runs on the ESB and serves a real request; and how long does that take end to end?
 *
 * <p>Cases: the 35 complete ones from llm-benchmark (20 clean L1, 15 noisy L2: Persian digits,
 * shuffled order, decoy addresses). Every proposal is confirmed as proposed — this measures the
 * model's answer, not a reviewer's correction. A case passes only if the chosen template, every
 * variable, and the live call through the gateway are all right.
 */
@DisplayName("B1 [سؤال ۱] تعریف سرویس از روی سند: قالب درست، پارامتر درست، سرویس در حال اجرا")
class ServiceDefinitionBenchmarkTest extends BenchmarkBase {

    static List<BenchmarkCase> cases() throws Exception {
        return BenchmarkCase.load("L1", "L2");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void definesServiceThatServesTraffic(BenchmarkCase original) throws Exception {
        Map<String, GroovyTemplate> catalog = seedCatalog();
        int pvPort = freePort();
        BenchmarkCase c = original.withProviderOrigin("http://127.0.0.1:" + pvPort);
        int expectedGwPort = Integer.parseInt(c.variables().get("gwPort"));
        // a busy port is a failure, not a skip: a skipped case would silently drop out of the
        // percentages (and a port an earlier service never released is a defect worth seeing)
        long portDeadline = System.currentTimeMillis() + 10_000;
        while (!portFree(expectedGwPort) && System.currentTimeMillis() < portDeadline) {
            Thread.sleep(200);
        }
        assertThat(portFree(expectedGwPort)).as("gateway port %d is still in use", expectedGwPort).isTrue();

        String marker = "provider-" + c.id();
        try (AutoCloseable provider = startProvider(c, marker, pvPort)) {
            Run run = converse(c, confirmAll, false);

            ServiceGroovyConfig config = configOf(run);
            String chosen = config == null ? null : nameOf(catalog, config.getGroovyTemplate().getId());
            Map<String, Object> actual = config == null ? Map.of() : config.getVariableValues();
            Map<String, String> wrong = new LinkedHashMap<>();
            c.variables().forEach((k, expected) -> {
                Object got = actual.get(k);
                if (got == null || !expected.equals(String.valueOf(got).trim())) {
                    wrong.put(k, "expected " + expected + ", got " + got);
                }
            });

            Boolean served = null;
            String gatewayDetail = null;
            if (config != null && chosen != null && chosen.equals(c.templateName()) && wrong.isEmpty()) {
                GatewayResult result = callThroughGateway(run, chosen, actual, marker);
                served = result.reachedProvider();
                gatewayDetail = result.detail();
                if (served) {
                    run.servedAfterMs = System.currentTimeMillis() - run.startedAtMs - run.backoffMs;
                }
            }

            report("B1", run, mapOf(
                    "chosenTemplate", chosen,
                    "executedVariables", actual,
                    "templateCorrect", c.templateName().equals(chosen),
                    "wrongVariables", wrong,
                    "variablesCorrect", config != null && wrong.isEmpty(),
                    "served", served,
                    "gatewayDetail", gatewayDetail,
                    "endToEndMs", run.servedAfterMs,
                    "askedInstead", run.asked && config == null));

            assertThat(config).as("no service_groovy_config was executed; events: %s", run.events).isNotNull();
            assertThat(chosen).as("template").isEqualTo(c.templateName());
            assertThat(wrong).as("variables").isEmpty();
            assertThat(served).as("gateway call reached the provider: %s", gatewayDetail).isTrue();
        }
    }

    private static String nameOf(Map<String, GroovyTemplate> catalog, Long id) {
        return catalog.values().stream().filter(t -> t.getId().equals(id)).map(GroovyTemplate::getName)
                .findFirst().orElse("unknown-template-" + id);
    }

    private static boolean portFree(int port) {
        try (ServerSocket s = new ServerSocket(port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
