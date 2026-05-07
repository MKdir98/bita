package ir.bita.esm.llm.tool;

import ir.bita.common.domain.ComponentType;
import ir.bita.esm.route.command.CreateRouteCommand;
import ir.bita.esm.route.handler.CreateRouteHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool to create a route.
 */
@Component
@RequiredArgsConstructor
public class CreateRouteTool implements LlmTool {

    private final CreateRouteHandler routeHandler;

    @Override
    public String getName() {
        return "create_route";
    }

    @Override
    public String getDescription() {
        return "یک مسیر (route) جدید برای سرویس ایجاد می‌کند. مسیر، ارتباط بین endpoint ورودی و خروجی را تعریف می‌کند و داده را از یک نقطه به نقطه دیگر منتقل می‌کند.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "serviceId", Map.of(
                                "type", "integer",
                                "description", "شناسه سرویس"
                        ),
                        "routeName", Map.of(
                                "type", "string",
                                "description", "نام مسیر"
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "توضیحات مسیر"
                        ),
                        "fromUri", Map.of(
                                "type", "string",
                                "description", "URI ورودی (مثلاً direct:input یا cxf:bean:paymentWs)"
                        ),
                        "toUri", Map.of(
                                "type", "string",
                                "description", "URI خروجی (مثلاً http://backend-service:8080)"
                        )
                ),
                "required", new String[]{"serviceId", "routeName", "fromUri", "toUri"}
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long serviceId = ((Number) arguments.get("serviceId")).longValue();
        String routeName = (String) arguments.get("routeName");
        String description = (String) arguments.get("description");
        String fromUri = (String) arguments.get("fromUri");
        String toUri = (String) arguments.get("toUri");

        var route = routeHandler.handle(CreateRouteCommand.builder()
                .serviceId(serviceId)
                .name(routeName)
                .description(description)
                .fromEndpoint(CreateRouteCommand.EndpointConfig.builder()
                        .name(routeName + "-from")
                        .uri(fromUri)
                        .build())
                .toEndpoint(CreateRouteCommand.EndpointConfig.builder()
                        .name(routeName + "-to")
                        .uri(toUri)
                        .build())
                .build());

        return Map.of(
                "success", true,
                "routeId", route.getId(),
                "routeName", route.getName()
        );
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }
}
