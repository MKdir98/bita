package ir.bita.esm.llm.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool to ask questions to the user.
 */
@Component
public class AskQuestionTool implements LlmTool {

    @Override
    public String getName() {
        return "ask_question";
    }

    @Override
    public String getDescription() {
        return "از کاربر سوال می‌پرسد و منتظر پاسخ می‌ماند.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of(
                                "type", "string",
                                "description", "سوال برای کاربر"
                        ),
                        "context", Map.of(
                                "type", "string",
                                "description", "زمینه و توضیحات سوال"
                        )
                ),
                "required", new String[]{"question"}
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        String question = (String) arguments.get("question");
        String context = (String) arguments.getOrDefault("context", "");

        return Map.of(
                "success", true,
                "message", "سوال ارسال شد. منتظر پاسخ کاربر...",
                "question", question,
                "context", context
        );
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // سوال نیاز به تایید ندارد
    }
}
