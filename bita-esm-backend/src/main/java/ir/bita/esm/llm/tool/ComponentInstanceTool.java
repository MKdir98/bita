package ir.bita.esm.llm.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool to create or update component instances.
 * Note: Components are tied to routes, so they should be created as part of route creation.
 */
@Component
public class ComponentInstanceTool implements LlmTool {

    @Override
    public String getName() {
        return "component_instance";
    }

    @Override
    public String getDescription() {
        return "ساخت یا ویرایش نمونه component. توجه: component ها به route وابسته هستند و باید به عنوان بخشی از route ساخته شوند.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "id", Map.of(
                                "type", "integer",
                                "description", "شناسه component برای ویرایش"
                        ),
                        "templateId", Map.of(
                                "type", "integer",
                                "description", "شناسه قالب component"
                        ),
                        "routeId", Map.of(
                                "type", "integer",
                                "description", "شناسه route که component به آن تعلق دارد"
                        ),
                        "name", Map.of(
                                "type", "string",
                                "description", "نام component"
                        ),
                        "config", Map.of(
                                "type", "object",
                                "description", "مقادیر پارامترهای قالب"
                        )
                ),
                "required", new String[]{}
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        // TODO: Implement component instance creation/update
        return Map.of(
                "success", false,
                "message", "ساخت component instance هنوز پیاده‌سازی نشده است. component ها باید به عنوان بخشی از route ساخته شوند."
        );
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }
}
