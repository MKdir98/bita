package ir.bita.esm.llm.tool;

import ir.bita.esm.route.command.CreateRouteTemplateCommand;
import ir.bita.esm.route.command.UpdateRouteTemplateCommand;
import ir.bita.esm.route.handler.CreateRouteTemplateHandler;
import ir.bita.esm.route.handler.UpdateRouteTemplateHandler;
import ir.bita.esm.route.repository.EndpointTemplateRepository;
import ir.bita.esm.route.repository.RouteTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool to create or update route templates.
 */
@Component
@RequiredArgsConstructor
public class RouteTemplateTool implements LlmTool {

    private final CreateRouteTemplateHandler createHandler;
    private final UpdateRouteTemplateHandler updateHandler;
    private final RouteTemplateRepository repository;
    private final EndpointTemplateRepository endpointTemplateRepository;

    @Override
    public String getName() {
        return "route_template";
    }

    @Override
    public String getDescription() {
        return "ساخت یا ویرایش قالب route. اگر id داده شود، قالب موجود ویرایش می‌شود، در غیر این صورت قالب جدید ساخته می‌شود.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "id", Map.of(
                                "type", "integer",
                                "description", "شناسه قالب برای ویرایش (null برای ساخت جدید)"
                        ),
                        "name", Map.of(
                                "type", "string",
                                "description", "نام قالب (فقط حروف انگلیسی کوچک، اعداد و -)"
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "توضیحات قالب"
                        ),
                        "fromEndpointTemplateId", Map.of(
                                "type", "integer",
                                "description", "شناسه قالب endpoint ورودی"
                        ),
                        "toEndpointTemplateId", Map.of(
                                "type", "integer",
                                "description", "شناسه قالب endpoint خروجی"
                        ),
                        "configSchema", Map.of(
                                "type", "object",
                                "description", "JSON Schema پارامترهای قابل تنظیم"
                        ),
                        "componentConfig", Map.of(
                                "type", "object",
                                "description", "پیکربندی component ها"
                        ),
                        "category", Map.of(
                                "type", "string",
                                "description", "دسته‌بندی قالب"
                        )
                ),
                "required", new String[]{}
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> arguments) {
        try {
            Long id = arguments.get("id") != null ?
                    ((Number) arguments.get("id")).longValue() : null;

            // Validate name format
            String name = (String) arguments.get("name");
            if (name != null && !name.matches("^[a-z0-9-]+$")) {
                return Map.of(
                        "error", true,
                        "message", "نام فقط می‌تواند شامل حروف انگلیسی کوچک، اعداد و - باشد"
                );
            }

            if (id != null) {
                // Update existing template
                if (!repository.existsById(id)) {
                    return Map.of("error", true, "message", "قالب با این id وجود ندارد");
                }

                Long fromEndpointTemplateId = arguments.get("fromEndpointTemplateId") != null ?
                        ((Number) arguments.get("fromEndpointTemplateId")).longValue() : null;
                Long toEndpointTemplateId = arguments.get("toEndpointTemplateId") != null ?
                        ((Number) arguments.get("toEndpointTemplateId")).longValue() : null;

                var command = UpdateRouteTemplateCommand.builder()
                        .id(id)
                        .name(name)
                        .description((String) arguments.get("description"))
                        .fromEndpointTemplateId(fromEndpointTemplateId)
                        .toEndpointTemplateId(toEndpointTemplateId)
                        .configSchema((Map<String, Object>) arguments.get("configSchema"))
                        .componentConfig((Map<String, Object>) arguments.get("componentConfig"))
                        .category((String) arguments.get("category"))
                        .build();

                var result = updateHandler.handle(command);
                return Map.of(
                        "success", true,
                        "id", result.getId(),
                        "name", result.getName(),
                        "message", "قالب route با موفقیت به‌روزرسانی شد"
                );
            } else {
                // Create new template
                if (name == null) {
                    return Map.of("error", true, "message", "نام برای ساخت قالب جدید الزامی است");
                }

                Long fromEndpointTemplateId = arguments.get("fromEndpointTemplateId") != null ?
                        ((Number) arguments.get("fromEndpointTemplateId")).longValue() : null;
                Long toEndpointTemplateId = arguments.get("toEndpointTemplateId") != null ?
                        ((Number) arguments.get("toEndpointTemplateId")).longValue() : null;

                var command = CreateRouteTemplateCommand.builder()
                        .name(name)
                        .description((String) arguments.get("description"))
                        .fromEndpointTemplateId(fromEndpointTemplateId)
                        .toEndpointTemplateId(toEndpointTemplateId)
                        .configSchema((Map<String, Object>) arguments.get("configSchema"))
                        .componentConfig((Map<String, Object>) arguments.get("componentConfig"))
                        .category((String) arguments.get("category"))
                        .build();

                var result = createHandler.handle(command);
                return Map.of(
                        "success", true,
                        "id", result.getId(),
                        "name", result.getName(),
                        "message", "قالب route با موفقیت ساخته شد"
                );
            }
        } catch (IllegalArgumentException e) {
            return Map.of("error", true, "message", e.getMessage());
        } catch (Exception e) {
            return Map.of("error", true, "message", "خطا در پردازش: " + e.getMessage());
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }
}
