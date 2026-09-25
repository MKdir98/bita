package ir.bita.system;

import ir.bita.esm.llm.entity.ToolExecutionStatus;
import ir.bita.system.support.BenchmarkBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S1 — research question 1 (no matching template): when none of the stored GroovyTemplates can
 * serve the request, does the LLM say so instead of forcing the request into an unrelated
 * template?
 *
 * <p>Setup: the benchmark's six templates (REST and SOAP only). Action: a document asking for an
 * MQTT service. Every proposal is confirmed as proposed (this measures the model, not a
 * reviewer). Expectation: no service_groovy_config is executed; the model tells the user,
 * in text or with ask_question, that no template fits.
 */
@DisplayName("S1 [سؤال ۱] وقتی هیچ قالبی جور نیست، مدل ادعای ساخت نمی‌کند")
class NoMatchingTemplateTest extends BenchmarkBase {

    private static final String MQTT_REQUEST = """
            موضوع: درخواست راه‌اندازی سرویس «اعلان دمای انبار» روی گذرگاه بیتا

            با سلام، سنسورهای دمای انبارهای ما روی یک بروکر MQTT پیام منتشر می‌کنند و باید از طریق بیتا
            در اختیار سازمان‌های مشترک قرار گیرند. این سرویس REST یا SOAP نیست.

            مشخصات فنی:
            - نام فنی سرویس warehouse-temperature، نسخهٔ v1، و مجموعهٔ logistics است.
            - آدرس بروکر: tcp://10.20.30.40:1883
            - موضوع (topic): sensors/warehouse/temperature
            - پورت اختصاصی گذرگاه: 18600
            """;

    @Test
    void saysNoTemplateFitsInsteadOfForcingOne() throws Exception {
        seedCatalog();
        Conversation chat = startConversation("S1-mqtt");
        chat.send(MQTT_REQUEST);
        for (int hop = 0; hop < 6 && chat.last.getPendingToolExecution() != null; hop++) {
            chat.answerPending(true);
        }

        boolean configured = chat.run.executed.stream().anyMatch(e -> "service_groovy_config".equals(e.getToolName())
                && e.getStatus() == ToolExecutionStatus.EXECUTED);
        report("S1", chat.run, mapOf("configuredWithUnrelatedTemplate", configured, "toldUser", chat.run.asked));

        assertThat(configured).as("an unrelated template was configured; events: %s", chat.run.events).isFalse();
        assertThat(chat.run.asked).as("the model never told the user; events: %s", chat.run.events).isTrue();
    }
}
