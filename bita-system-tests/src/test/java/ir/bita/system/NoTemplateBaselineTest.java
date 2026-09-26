package ir.bita.system;

import ir.bita.esm.llm.entity.ToolExecution;
import ir.bita.esm.llm.entity.ToolExecutionStatus;
import ir.bita.esm.route.entity.ServiceGroovyConfig;
import ir.bita.system.support.BenchmarkBase;
import ir.bita.system.support.BenchmarkCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

/**
 * Ablation for research question 1 — the same 35 documents as B1, with an empty template catalog:
 * the model has to write every service's script itself (through groovy_template, with the same
 * checks and sandboxed trial run as S3), then configure the service from it.
 *
 * <p>Setup: no templates; the provider of each document running. Action: the conversation; the
 * reviewer confirms every proposal. Measured, not asserted: whether a consumer calling the service
 * through a real ESB reaches the provider, as in B1. The difference to B1 is the effect of the
 * catalog.
 */
@DisplayName("پایه: همان ۳۵ سند B1 بدون کاتالوگ قالب (مدل کد می‌نویسد)")
class NoTemplateBaselineTest extends BenchmarkBase {

    static List<BenchmarkCase> cases() throws Exception {
        return BenchmarkCase.load("L1", "L2");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void modelWritesTheScriptItself(BenchmarkCase original) throws Exception {
        emptyCatalog();
        int pvPort = freePort();
        BenchmarkCase c = original.withProviderOrigin("http://127.0.0.1:" + pvPort);
        String marker = "provider-" + c.id();
        try (AutoCloseable provider = startProvider(c, marker, pvPort)) {
            Run run = converse(c, confirmAll, false);
            ServiceGroovyConfig config = configOf(run);
            int created = 0;
            int rejected = 0;
            for (ToolExecution e : run.executed) {
                if ("groovy_template".equals(e.getToolName())) {
                    if (e.getStatus() == ToolExecutionStatus.EXECUTED) {
                        created++;
                    } else if (e.getStatus() == ToolExecutionStatus.FAILED) {
                        rejected++;
                    }
                }
            }
            GatewayResult served = config == null ? new GatewayResult(false, "no service_groovy_config was executed")
                    : callThroughGateway(run, c.templateName(), config.getVariableValues(), marker);
            if (served.reachedProvider() && run.servedAfterMs == null) {
                run.servedAfterMs = System.currentTimeMillis() - run.startedAtMs - run.backoffMs;
            }
            report("NOTPL", run, mapOf(
                    "templatesCreated", created, "draftsRejected", rejected,
                    "configured", config != null, "served", served.reachedProvider(),
                    "gatewayDetail", served.detail(), "endToEndMs", served.reachedProvider() ? run.servedAfterMs : null));
        }
    }
}
