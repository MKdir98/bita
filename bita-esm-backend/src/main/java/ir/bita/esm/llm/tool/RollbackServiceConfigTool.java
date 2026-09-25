package ir.bita.esm.llm.tool;

import ir.bita.esm.route.entity.ServiceConfigVersion;
import ir.bita.esm.route.service.ServiceConfigVersionService;
import ir.bita.esm.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Makes an earlier minor version of a service's configuration active again (e.g. 1.7 → 1.6),
 * on the same address, without restarting the ESB. Needs the user's confirmation like any
 * other change.
 */
@Component
@RequiredArgsConstructor
public class RollbackServiceConfigTool implements LlmTool {

    private final ServiceRepository serviceRepository;
    private final ServiceConfigVersionService versionService;

    @Override
    public String getName() {
        return "rollback_service_config";
    }

    @Override
    public String getDescription() {
        return "بازگرداندن پیکربندی سرویس به یک نسخهٔ فرعی قبلی (مثلاً از ۱.۷ به ۱.۶) روی همان آدرس، بدون راه‌اندازی مجدد ESB. "
                + "اگر نسخه را نمی‌دانی، ابتدا بدون version فراخوانی کن تا فهرست نسخه‌ها برگردد.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "serviceId", Map.of("type", "integer", "description", "شناسه سرویس"),
                        "version", Map.of("type", "string", "description", "نسخهٔ مقصد، مثلاً 1.6")
                ),
                "required", new String[]{"serviceId"}
        );
    }

    @Override
    @Transactional
    public Map<String, Object> execute(Map<String, Object> arguments) {
        if (arguments.get("serviceId") == null) {
            return Map.of("error", true, "message", "serviceId الزامی است");
        }
        long serviceId = ((Number) arguments.get("serviceId")).longValue();
        var service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            return Map.of("error", true, "message", "سرویس یافت نشد: " + serviceId);
        }
        List<String> labels = versionService.versions(serviceId).stream()
                .map(v -> v.label() + (v.isActive() ? " (فعال)" : "")).toList();
        Object requested = arguments.get("version");
        if (requested == null || String.valueOf(requested).isBlank()) {
            return Map.of("success", true, "versions", labels);
        }
        String label = String.valueOf(requested).trim();
        int minor;
        try {
            minor = Integer.parseInt(label.contains(".") ? label.substring(label.lastIndexOf('.') + 1) : label);
        } catch (NumberFormatException e) {
            return Map.of("error", true, "message", "نسخهٔ نامعتبر: " + label + "؛ نسخه‌های موجود: " + labels);
        }
        try {
            ServiceConfigVersion active = versionService.rollback(service, minor);
            return Map.of("success", true, "serviceId", serviceId, "activeVersion", active.label(),
                    "message", "سرویس به نسخهٔ " + active.label() + " بازگشت");
        } catch (IllegalArgumentException e) {
            return Map.of("error", true, "message", e.getMessage() + "؛ نسخه‌های موجود: " + labels);
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }
}
