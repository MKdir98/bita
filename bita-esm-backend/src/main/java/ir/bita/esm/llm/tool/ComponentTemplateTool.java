package ir.bita.esm.llm.tool;

import ir.bita.common.domain.ComponentType;
import ir.bita.esm.route.command.CreateComponentTemplateCommand;
import ir.bita.esm.route.command.UpdateComponentTemplateCommand;
import ir.bita.esm.route.handler.CreateComponentTemplateHandler;
import ir.bita.esm.route.handler.UpdateComponentTemplateHandler;
import ir.bita.esm.route.repository.ComponentTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool to create or update component templates.
 */
@Component
@RequiredArgsConstructor
public class ComponentTemplateTool implements LlmTool {

    private final CreateComponentTemplateHandler createHandler;
    private final UpdateComponentTemplateHandler updateHandler;
    private final ComponentTemplateRepository repository;

    @Override
    public String getName() {
        return "component_template";
    }

    @Override
    public String getDescription() {
        return "ساخت یا ویرایش قالب component. اگر id داده شود، قالب موجود ویرایش می‌شود، در غیر این صورت قالب جدید ساخته می‌شود.";
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
                        "componentType", Map.of(
                                "type", "string",
                                "enum", new String[]{"PROCESSOR", "BEAN"},
                                "description", "نوع component (PROCESSOR یا BEAN)"
                        ),
                        "className", Map.of(
                                "type", "string",
                                "description", "نام کامل کلاس Java"
                        ),
                        "configSchema", Map.of(
                                "type", "object",
                                "description", "JSON Schema پارامترهای قابل تنظیم"
                        ),
                        "groovyCode", Map.of(
                                "type", "string",
                                "description", "کد Groovy کلاس component — در اسکریپت نهایی سرویس inline می‌شود"
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

            List<String> storage = new java.util.ArrayList<>(
                    DataStoragePolicy.violations("groovyCode", (String) arguments.get("groovyCode")));
            storage.addAll(DataStoragePolicy.violations("className", (String) arguments.get("className")));
            if (!storage.isEmpty()) {
                return Map.of("error", true, "message", DataStoragePolicy.message(storage));
            }

            // Parse component type
            ComponentType componentType = null;
            if (arguments.get("componentType") != null) {
                try {
                    componentType = ComponentType.valueOf((String) arguments.get("componentType"));
                } catch (IllegalArgumentException e) {
                    return Map.of("error", true, "message", "نوع component نامعتبر است. باید PROCESSOR یا BEAN باشد");
                }
            }

            if (id != null) {
                // Update existing template
                if (!repository.existsById(id)) {
                    return Map.of("error", true, "message", "قالب با این id وجود ندارد");
                }

                var command = UpdateComponentTemplateCommand.builder()
                        .id(id)
                        .name(name)
                        .description((String) arguments.get("description"))
                        .componentType(componentType)
                        .className((String) arguments.get("className"))
                        .configSchema((Map<String, Object>) arguments.get("configSchema"))
                        .groovyCode((String) arguments.get("groovyCode"))
                        .build();

                var result = updateHandler.handle(command);
                return Map.of(
                        "success", true,
                        "id", result.getId(),
                        "name", result.getName(),
                        "message", "قالب component با موفقیت به‌روزرسانی شد"
                );
            } else {
                // Create new template
                if (name == null) {
                    return Map.of("error", true, "message", "نام برای ساخت قالب جدید الزامی است");
                }
                if (componentType == null) {
                    return Map.of("error", true, "message", "componentType برای ساخت قالب جدید الزامی است");
                }
                if (arguments.get("className") == null) {
                    return Map.of("error", true, "message", "className برای ساخت قالب جدید الزامی است");
                }

                var command = CreateComponentTemplateCommand.builder()
                        .name(name)
                        .description((String) arguments.get("description"))
                        .componentType(componentType)
                        .className((String) arguments.get("className"))
                        .configSchema((Map<String, Object>) arguments.get("configSchema"))
                        .groovyCode((String) arguments.get("groovyCode"))
                        .build();

                var result = createHandler.handle(command);
                return Map.of(
                        "success", true,
                        "id", result.getId(),
                        "name", result.getName(),
                        "message", "قالب component با موفقیت ساخته شد"
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
