package ir.bita.esm.llm.tool;

import ir.bita.esm.route.query.RouteQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool to request data from internal APIs.
 */
@Component
@RequiredArgsConstructor
public class RequestDataTool implements LlmTool {

    private final ListClientsTool listClientsTool;
    private final ListServicesTool listServicesTool;
    private final ListTemplatesTool listTemplatesTool;
    private final RouteQueryService routeQueryService;

    @Override
    public String getName() {
        return "request_data";
    }

    @Override
    public String getDescription() {
        return "درخواست داده از API های داخلی سیستم (لیست سازمان‌ها، سرویس‌ها، قالب‌ها و نمونه‌ها).";
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
                                        "list_endpoint_templates",
                                        "list_component_templates",
                                        "list_route_templates",
                                        "list_endpoint_instances",
                                        "list_component_instances",
                                        "list_route_instances"
                                },
                                "description", "نوع داده مورد نیاز"
                        ),
                        "filters", Map.of(
                                "type", "object",
                                "description", "فیلترهای اختیاری (search, page, size, category, type, serviceId, ...)"
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
            case "list_endpoint_templates" -> {
                Map<String, Object> templateFilters = new HashMap<>(filters);
                templateFilters.put("type", "endpoint");
                yield listTemplatesTool.execute(templateFilters);
            }
            case "list_component_templates" -> {
                Map<String, Object> templateFilters = new HashMap<>(filters);
                templateFilters.put("type", "component");
                yield listTemplatesTool.execute(templateFilters);
            }
            case "list_route_templates" -> {
                Map<String, Object> templateFilters = new HashMap<>(filters);
                templateFilters.put("type", "route");
                yield listTemplatesTool.execute(templateFilters);
            }
            case "list_endpoint_instances" -> {
                int page = ((Number) filters.getOrDefault("page", 0)).intValue();
                int size = ((Number) filters.getOrDefault("size", 50)).intValue();
                var endpoints = routeQueryService.listEndpoints(PageRequest.of(page, size));
                yield Map.of("endpoints", endpoints.getContent(), "total", endpoints.getTotalElements());
            }
            case "list_component_instances" -> {
                int page = ((Number) filters.getOrDefault("page", 0)).intValue();
                int size = ((Number) filters.getOrDefault("size", 50)).intValue();
                var components = routeQueryService.listComponents(PageRequest.of(page, size));
                yield Map.of("components", components.getContent(), "total", components.getTotalElements());
            }
            case "list_route_instances" -> {
                Long serviceId = filters.get("serviceId") != null ?
                        ((Number) filters.get("serviceId")).longValue() : null;
                if (serviceId != null) {
                    var routes = routeQueryService.listRoutesByService(serviceId);
                    yield Map.of("routes", routes);
                } else {
                    int page = ((Number) filters.getOrDefault("page", 0)).intValue();
                    int size = ((Number) filters.getOrDefault("size", 50)).intValue();
                    var routes = routeQueryService.listRoutes(PageRequest.of(page, size));
                    yield Map.of("routes", routes.getContent(), "total", routes.getTotalElements());
                }
            }
            default -> Map.of("error", true, "message", "نوع داده نامعتبر است");
        };
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }
}
