package ir.bita.esm.llm.tool;

import ir.bita.esm.route.query.RouteQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool to request data from internal APIs.
 */
@Component
@RequiredArgsConstructor
public class RequestDataTool implements LlmTool {

    private final ListClientsTool listClientsTool;
    private final ListServicesTool listServicesTool;
    private final RouteQueryService routeQueryService;

    @Override
    public String getName() {
        return "request_data";
    }

    @Override
    public String getDescription() {
        return "درخواست داده از API های داخلی سیستم (لیست کلاینت‌ها، سرویس‌ها، قالب‌های component).";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "data_type", Map.of(
                                "type", "string",
                                "enum", new String[]{
                                        "list_clients",
                                        "list_services",
                                        "list_component_templates"
                                },
                                "description", "نوع داده مورد نیاز"
                        ),
                        "filters", Map.of(
                                "type", "object",
                                "description", "فیلترهای اختیاری (search, page, size)"
                        )
                ),
                "required", new String[]{"data_type"}
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> arguments) {
        String dataType = (String) arguments.get("data_type");
        Map<String, Object> filters = (Map<String, Object>) arguments.getOrDefault("filters", Map.of());

        return switch (dataType) {
            case "list_clients" -> listClientsTool.execute(filters);
            case "list_services" -> listServicesTool.execute(filters);
            case "list_component_templates" -> {
                int page = ((Number) filters.getOrDefault("page", 0)).intValue();
                int size = ((Number) filters.getOrDefault("size", 50)).intValue();
                var templates = routeQueryService.listComponentTemplates(PageRequest.of(page, size));
                yield Map.of("componentTemplates", templates.getContent(), "total", templates.getTotalElements());
            }
            default -> Map.of("error", true, "message", "نوع داده نامعتبر است");
        };
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }
}
