package ir.bita.esm.llm.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool to mark task as complete.
 */
@Component
public class CompleteTool implements LlmTool {

    @Override
    public String getName() {
        return "complete";
    }

    @Override
    public String getDescription() {
        return "کار را به اتمام می‌رساند و خلاصه‌ای از کارهای انجام شده را نمایش می‌دهد.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "summary", Map.of(
                                "type", "string",
                                "description", "خلاصه کارهای انجام شده"
                        ),
                        "created_items", Map.of(
                                "type", "array",
                                "description", "لیست آیتم‌های ساخته شده",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "type", Map.of("type", "string"),
                                                "id", Map.of("type", "integer"),
                                                "name", Map.of("type", "string")
                                        )
                                )
                        )
                ),
                "required", new String[]{"summary"}
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> arguments) {
        String summary = (String) arguments.get("summary");
        List<Map<String, Object>> createdItems = 
                (List<Map<String, Object>>) arguments.getOrDefault("created_items", List.of());

        return Map.of(
                "success", true,
                "message", summary,
                "created_items", createdItems,
                "completed", true
        );
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }
}
