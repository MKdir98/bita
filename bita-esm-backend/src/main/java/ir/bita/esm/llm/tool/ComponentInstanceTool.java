package ir.bita.esm.llm.tool;

import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.entity.ServiceConfigVersion;
import ir.bita.esm.route.repository.GroovyTemplateRepository;
import ir.bita.esm.route.service.ScriptAssemblyService;
import ir.bita.esm.route.service.ServiceConfigVersionService;
import ir.bita.esm.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM tool for assigning a GroovyTemplate to a service and setting its variable values.
 * Replaces the old route-linked component instance model.
 */
@Component
@RequiredArgsConstructor
public class ComponentInstanceTool implements LlmTool {

    private final GroovyTemplateRepository templateRepository;
    private final ServiceRepository serviceRepository;
    private final ScriptAssemblyService assemblyService;
    private final ServiceConfigVersionService versionService;

    @Override
    public String getName() {
        return "service_groovy_config";
    }

    @Override
    public String getDescription() {
        return "انتساب یک GroovyTemplate به سرویس و تنظیم مقادیر متغیرها. هر اجرا یک نسخهٔ فرعی جدید پیکربندی (مثلاً ۱.۷) می‌سازد و فعال می‌کند؛ نسخه‌های قبلی برای بازگشت باقی می‌مانند.";
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
                        "groovyTemplateId", Map.of(
                                "type", "integer",
                                "description", "شناسه GroovyTemplate"
                        ),
                        "variableValues", Map.of(
                                "type", "object",
                                "description", "مقادیر متغیرهای template به صورت Map<name, value>"
                        )
                ),
                "required", new String[]{"serviceId", "groovyTemplateId"}
        );
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> arguments) {
        try {
            Long serviceId = arguments.get("serviceId") != null
                    ? ((Number) arguments.get("serviceId")).longValue() : null;
            Long templateId = arguments.get("groovyTemplateId") != null
                    ? ((Number) arguments.get("groovyTemplateId")).longValue() : null;

            if (serviceId == null) return Map.of("error", true, "message", "serviceId الزامی است");
            if (templateId == null) return Map.of("error", true, "message", "groovyTemplateId الزامی است");

            var service = serviceRepository.findById(serviceId).orElse(null);
            if (service == null) return Map.of("error", true, "message", "سرویس یافت نشد: " + serviceId);

            var template = templateRepository.findByIdAndDeletedFalse(templateId).orElse(null);
            if (template == null) return Map.of("error", true, "message", "GroovyTemplate یافت نشد: " + templateId);

            Map<String, Object> varValues = arguments.get("variableValues") instanceof Map
                    ? (Map<String, Object>) arguments.get("variableValues")
                    : new HashMap<>();

            // the template's own variable declarations are the schema: a config missing a
            // required value, or with a value of the wrong type, is rejected here rather than
            // stored and left to fail when the ESB runs the script
            List<String> problems = validateVariables(template, varValues);
            if (!problems.isEmpty()) {
                return Map.of("error", true, "message",
                        "مقادیر متغیرهای قالب نامعتبر است: " + String.join("؛ ", problems));
            }

            List<String> storage = new ArrayList<>(DataStoragePolicy.violations(varValues));
            storage.addAll(DataStoragePolicy.violations("قالب " + template.getName(), template.getScriptText()));
            if (!storage.isEmpty()) {
                return Map.of("error", true, "message", DataStoragePolicy.message(storage));
            }

            String assembled = assemblyService.assemble(template);

            // every approved configuration is a new minor version (1.6 -> 1.7), made active;
            // earlier ones stay available for rollback_service_config
            ServiceConfigVersion version = versionService.recordAndActivate(service, template, varValues, assembled,
                    arguments.get("note") != null ? String.valueOf(arguments.get("note")) : null);

            return Map.of(
                    "success", true,
                    "serviceId", serviceId,
                    "groovyTemplateId", templateId,
                    "version", version.label(),
                    "message", "نسخهٔ " + version.label() + " پیکربندی سرویس ساخته و فعال شد"
            );
        } catch (Exception e) {
            return Map.of("error", true, "message", "خطا: " + e.getMessage());
        }
    }

    public static List<String> validateVariables(GroovyTemplate template, Map<String, Object> values) {
        List<String> problems = new ArrayList<>();
        if (template.getVariables() == null) {
            return problems;
        }
        for (GroovyTemplate.VariableMetadata v : template.getVariables()) {
            Object raw = values.get(v.getName());
            String value = raw == null ? null : String.valueOf(raw).trim();
            if (value == null || value.isEmpty()) {
                if (v.isRequired() && (v.getDefaultValue() == null || v.getDefaultValue().isBlank())) {
                    problems.add(v.getName() + " الزامی است");
                }
                continue;
            }
            if (v.getType() == null) {
                continue;
            }
            switch (v.getType()) {
                case PORT -> {
                    Integer port = parseInt(value);
                    if (port == null || port < 1 || port > 65535) {
                        problems.add(v.getName() + " باید پورت معتبر (۱ تا ۶۵۵۳۵) باشد: " + value);
                    }
                }
                case INT -> {
                    if (parseInt(value) == null) {
                        problems.add(v.getName() + " باید عدد صحیح باشد: " + value);
                    }
                }
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        problems.add(v.getName() + " باید true یا false باشد: " + value);
                    }
                }
                default -> {
                }
            }
        }
        return problems;
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }
}
