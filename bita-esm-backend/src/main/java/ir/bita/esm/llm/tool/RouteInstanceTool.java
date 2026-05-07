package ir.bita.esm.llm.tool;

import ir.bita.esm.route.command.CreateRouteCommand;
import ir.bita.esm.route.command.UpdateRouteCommand;
import ir.bita.esm.route.handler.CreateRouteHandler;
import ir.bita.esm.route.handler.UpdateRouteHandler;
import ir.bita.esm.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool to create or update route instances.
 */
@Component
@RequiredArgsConstructor
public class RouteInstanceTool implements LlmTool {

    private final CreateRouteHandler createHandler;
    private final UpdateRouteHandler updateHandler;
    private final RouteRepository repository;

    @Override
    public String getName() {
        return "route_instance";
    }

    @Override
    public String getDescription() {
        return "ساخت یا ویرایش نمونه route. route ارتباط بین endpoint های ورودی و خروجی را تعریف می‌کند.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "id", Map.of(
                                "type", "integer",
                                "description", "شناسه route برای ویرایش"
                        ),
                        "serviceId", Map.of(
                                "type", "integer",
                                "description", "شناسه سرویس"
                        ),
                        "name", Map.of(
                                "type", "string",
                                "description", "نام route"
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "توضیحات"
                        ),
                        "fromUri", Map.of(
                                "type", "string",
                                "description", "URI endpoint ورودی"
                        ),
                        "toUri", Map.of(
                                "type", "string",
                                "description", "URI endpoint خروجی"
                        ),
                        "active", Map.of(
                                "type", "boolean",
                                "description", "وضعیت فعال/غیرفعال"
                        )
                ),
                "required", new String[]{}
        );
    }

    @Override
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
                // Update existing route
                if (!repository.existsById(id)) {
                    return Map.of("error", true, "message", "route با این id وجود ندارد");
                }

                var command = UpdateRouteCommand.builder()
                        .routeId(id)
                        .name(name)
                        .description((String) arguments.get("description"))
                        .active(arguments.get("active") != null ?
                                (Boolean) arguments.get("active") : null)
                        .build();

                var result = updateHandler.handle(command);
                return Map.of(
                        "success", true,
                        "id", result.getId(),
                        "name", result.getName(),
                        "message", "route با موفقیت به‌روزرسانی شد"
                );
            } else {
                // Create new route
                if (arguments.get("serviceId") == null) {
                    return Map.of("error", true, "message", "serviceId برای ساخت route جدید الزامی است");
                }
                if (name == null) {
                    return Map.of("error", true, "message", "name برای ساخت route جدید الزامی است");
                }
                if (arguments.get("fromUri") == null) {
                    return Map.of("error", true, "message", "fromUri برای ساخت route جدید الزامی است");
                }
                if (arguments.get("toUri") == null) {
                    return Map.of("error", true, "message", "toUri برای ساخت route جدید الزامی است");
                }

                Long serviceId = ((Number) arguments.get("serviceId")).longValue();
                String fromUri = (String) arguments.get("fromUri");
                String toUri = (String) arguments.get("toUri");

                var command = CreateRouteCommand.builder()
                        .serviceId(serviceId)
                        .name(name)
                        .description((String) arguments.get("description"))
                        .fromEndpoint(CreateRouteCommand.EndpointConfig.builder()
                                .name(name + "-from")
                                .uri(fromUri)
                                .build())
                        .toEndpoint(CreateRouteCommand.EndpointConfig.builder()
                                .name(name + "-to")
                                .uri(toUri)
                                .build())
                        .build();

                var result = createHandler.handle(command);
                return Map.of(
                        "success", true,
                        "id", result.getId(),
                        "name", result.getName(),
                        "message", "route با موفقیت ساخته شد"
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
