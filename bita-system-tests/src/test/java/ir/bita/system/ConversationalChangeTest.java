package ir.bita.system;

import ir.bita.esm.route.entity.ServiceGroovyConfig;
import ir.bita.system.support.BenchmarkBase;
import ir.bita.system.support.BenchmarkCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S2 — research question 2 (human confirmation, change through the conversation): does a
 * proposal the user rejects stay unapplied, and does the user's requested change in the chat
 * end up — and only it — in what is finally applied?
 *
 * <p>Setup: benchmark document c01, the six templates. Action: confirm create_service; reject
 * the first service_groovy_config proposal and ask in the chat for a different gateway port;
 * confirm the re-proposal. Expectation: nothing is configured while the proposal is rejected;
 * the re-proposal carries the requested port; the applied configuration has that port and
 * every other value as in the document; a consumer calling the running service on the requested
 * port gets the provider's answer — the change is live, not only stored.
 */
@DisplayName("S2 [سؤال ۲] رد پیشنهاد و اصلاح از طریق گفتگو")
class ConversationalChangeTest extends BenchmarkBase {

    private static final String REQUESTED_PORT = "18096";

    @Test
    void rejectedProposalIsNotAppliedAndTheRequestedChangeIs() throws Exception {
        seedCatalog();
        int pvPort = freePort();
        BenchmarkCase c = BenchmarkCase.byId("c01").withProviderOrigin("http://127.0.0.1:" + pvPort);
        startProvider(c, "s2-provider", pvPort);
        Conversation chat = startConversation("S2-" + c.id());
        chat.send(c.document());

        assertThat(chat.confirmUntil("service_groovy_config", 4)).as("events: %s", chat.run.events).isTrue();
        chat.answerPending(false);
        ServiceGroovyConfig afterReject = configOf(chat.run);
        assertThat(afterReject).as("a rejected proposal must not be applied").isNull();

        chat.send("پیشنهاد رد شد؛ پورت اختصاصی گذرگاه باید " + REQUESTED_PORT + " باشد، بقیهٔ مقادیر همان بماند.");
        assertThat(chat.confirmUntil("service_groovy_config", 4)).as("no re-proposal; events: %s", chat.run.events).isTrue();
        Object proposedPort = ((Map<?, ?>) chat.last.getPendingToolExecution().getArguments().get("variableValues")).get("gwPort");
        chat.answerPending(true);

        ServiceGroovyConfig applied = configOf(chat.run);
        GatewayResult served = applied == null ? new GatewayResult(false, "no configuration")
                : callThroughGateway(chat.run, c.templateName(), applied.getVariableValues(), "s2-provider");
        report("S2", chat.run, mapOf("proposedPortAfterChange", proposedPort,
                "appliedVariables", applied == null ? null : applied.getVariableValues(),
                "servedOnRequestedPort", served.reachedProvider(), "gatewayDetail", served.detail()));

        assertThat(String.valueOf(proposedPort)).as("re-proposal port").isEqualTo(REQUESTED_PORT);
        assertThat(applied).isNotNull();
        assertThat(String.valueOf(applied.getVariableValues().get("gwPort"))).isEqualTo(REQUESTED_PORT);
        c.variables().forEach((k, v) -> {
            if (!"gwPort".equals(k)) {
                assertThat(String.valueOf(applied.getVariableValues().get(k))).as(k).isEqualTo(v);
            }
        });
        assertThat(served.reachedProvider()).as("call on port %s: %s", REQUESTED_PORT, served.detail()).isTrue();
    }
}
