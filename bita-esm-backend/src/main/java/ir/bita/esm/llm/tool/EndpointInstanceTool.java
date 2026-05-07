package ir.bita.esm.llm.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool to create or update endpoint instances.
 * Note: Full implementation requires endpoint creation/update handlers.
 * For now, this is a placeholder that returns instructions.
 */
@Component
public class EndpointInstanceTool implements LlmTool {

    @Override
    public String getName() {
        return "endpoint_instance";
    }

    @Override
    public String getDescription() {
        return "ساخت یا ویرایش نمونه endpoint از یک قالب.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "id", Map.of(
                                "type", "integer",
                                "description", "شناسه endpoint برای ویرایش"
                        ),
                        "templateId", Map.of(
                                "type", "integer",
                                "description", "شناسه قالب endpoint"
                        ),
                        "name", Map.of(
                                "type", "string",
                                "description", "نام endpoint"
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "توضیحات"
                        ),
                        "config", Map.of(
                                "type", "object",
                                "description", "مقادیر پارامترهای قالب"
                        ),
                        "defaultRateLimit", Map.of(
                                "type", "integer",
                                "description", "محدودیت نرخ"
                        )
                ),
                "required", new String[]{}
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        // TODO: Implement endpoint instance creation/update
        // For now, return a message that this feature is under development
        return Map.of(
                "success", false,
                "message", "ساخت endpoint instance هنوز پیاده‌سازی نشده است. لطفاً از create_route استفاده کنید که endpoint ها را به صورت خودکار ایجاد می‌کند."
        );
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }
}
