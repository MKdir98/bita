package ir.bita.esm.llm.tool;

import ir.bita.esm.route.query.RouteQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool to list available templates.
 */
@Component
@RequiredArgsConstructor
public class ListTemplatesTool implements LlmTool {

    private final RouteQueryService routeQueryService;

    @Override
    public String getName() {
        return "list_templates";
    }

    @Override
    public String getDescription() {
        return "لیست قالب‌های موجود (endpoint, component, route) را برمی‌گرداند. قالب‌ها الگوهای قابل استفاده مجدد برای ساخت endpoint، component و route هستند.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "string",
                                "enum", new String[]{"endpoint", "component", "route"},
                                "description", "نوع قالب"
                        ),
                        "category", Map.of(
                                "type", "string",
                                "description", "فیلتر بر اساس دسته‌بندی"
                        )
                ),
                "required", new String[]{"type"}
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        String type = (String) arguments.get("type");
        String category = (String) arguments.get("category");

        Map<String, Object> result = new HashMap<>();

        switch (type) {
            case "endpoint" -> {
                if (category != null) {
                    result.put("templates", routeQueryService.listEndpointTemplatesByCategory(category));
                } else {
                    result.put("templates", routeQueryService.listEndpointTemplates(PageRequest.of(0, 50)).getContent());
                }
            }
            case "component" -> result.put("templates", routeQueryService.listComponentTemplates(PageRequest.of(0, 50)).getContent());
            case "route" -> result.put("templates", routeQueryService.listRouteTemplates(PageRequest.of(0, 50)).getContent());
            default -> result.put("error", "نوع قالب نامعتبر است");
        }

        return result;
    }
}
