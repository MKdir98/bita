package ir.bita.esm.llm.tool;

import ir.bita.esm.route.command.CreateEndpointTemplateCommand;
import ir.bita.esm.route.command.UpdateEndpointTemplateCommand;
import ir.bita.esm.route.handler.CreateEndpointTemplateHandler;
import ir.bita.esm.route.handler.UpdateEndpointTemplateHandler;
import ir.bita.esm.route.repository.EndpointTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool to create or update endpoint templates.
 */
@Component
@RequiredArgsConstructor
public class EndpointTemplateTool implements LlmTool {

    private final CreateEndpointTemplateHandler createHandler;
    private final UpdateEndpointTemplateHandler updateHandler;
    private final EndpointTemplateRepository repository;

    @Override
    public String getName() {
        return "endpoint_template";
    }

    @Override
    public String getDescription() {
        return "ساخت یا ویرایش قالب endpoint. اگر id داده شود، قالب موجود ویرایش می‌شود، در غیر این صورت قالب جدید ساخته می‌شود.";
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
                        "camelYaml", Map.of(
                                "type", "string",
                                "description", "تعریف Camel YAML با placeholder ها (مثل {{serviceName}})"
                        ),
                        "category", Map.of(
                                "type", "string",
                                "description", "دسته‌بندی (soap, rest, file, ...)"
                        ),
                        "configSchema", Map.of(
                                "type", "object",
                                "description", "JSON Schema پارامترهای قابل تنظیم"
                        ),
                        "defaultRateLimit", Map.of(
                                "type", "integer",
                                "description", "محدودیت نرخ پیش‌فرض"
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

                var command = UpdateEndpointTemplateCommand.builder()
                        .id(id)
                        .name(name)
                        .description((String) arguments.get("description"))
                        .camelYaml((String) arguments.get("camelYaml"))
                        .configSchema((Map<String, Object>) arguments.get("configSchema"))
                        .defaultRateLimit(arguments.get("defaultRateLimit") != null ?
                                ((Number) arguments.get("defaultRateLimit")).intValue() : null)
                        .category((String) arguments.get("category"))
                        .build();

                var result = updateHandler.handle(command);
                return Map.of(
                        "success", true,
                        "id", result.getId(),
                        "name", result.getName(),
                        "message", "قالب endpoint با موفقیت به‌روزرسانی شد"
                );
            } else {
                // Create new template
                if (name == null) {
                    return Map.of("error", true, "message", "نام برای ساخت قالب جدید الزامی است");
                }
                if (arguments.get("camelYaml") == null) {
                    return Map.of("error", true, "message", "camelYaml برای ساخت قالب جدید الزامی است");
                }

                var command = CreateEndpointTemplateCommand.builder()
                        .name(name)
                        .description((String) arguments.get("description"))
                        .camelYaml((String) arguments.get("camelYaml"))
                        .configSchema((Map<String, Object>) arguments.get("configSchema"))
                        .defaultRateLimit(arguments.get("defaultRateLimit") != null ?
                                ((Number) arguments.get("defaultRateLimit")).intValue() : null)
                        .category((String) arguments.get("category"))
                        .build();

                var result = createHandler.handle(command);
                return Map.of(
                        "success", true,
                        "id", result.getId(),
                        "name", result.getName(),
                        "message", "قالب endpoint با موفقیت ساخته شد"
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
