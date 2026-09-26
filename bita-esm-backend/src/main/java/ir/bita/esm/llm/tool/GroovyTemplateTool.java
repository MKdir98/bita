package ir.bita.esm.llm.tool;

import ir.bita.common.domain.VariableType;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.repository.GroovyTemplateRepository;
import ir.bita.esm.route.service.EsbTemplateTrial;
import lombok.RequiredArgsConstructor;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The second path, for when no template in the catalog fits: the model drafts a new
 * GroovyTemplate and, once the human confirms it, it joins the catalog. Services are still only
 * ever built from templates — this adds one, it does not configure a service from free code.
 *
 * <p>What is checked here, before anything is stored: the storage policy, that the script
 * guards access the way every catalog template does (a consumer's API key, then its grant), that
 * the gateway variables are declared and every other declared variable is used, and that the
 * script parses. Then the sandbox ESB trial-runs it with the ESB's own libraries — compiled, started
 * and called in front of a stand-in provider, without and with an API key — which is where a class
 * that does not exist or a method called with the wrong arguments shows up. Any refusal goes back
 * to the model with the reason, so it can correct the draft (which again needs confirmation).
 */
@Component
@RequiredArgsConstructor
public class GroovyTemplateTool implements LlmTool {

    private static final Pattern NAME = Pattern.compile("^[a-z0-9-]+$");

    private final GroovyTemplateRepository templateRepository;
    private final EsbTemplateTrial trial;

    @Override
    public String getName() {
        return "groovy_template";
    }

    @Override
    public String getDescription() {
        return "افزودن یک GroovyTemplate تازه به کاتالوگ، فقط وقتی هیچ قالب موجودی با نیاز جور نیست. "
                + "بعد از تأیید کاربر، سرویس با service_groovy_config از همین قالب ساخته می‌شود.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string",
                                "description", "نام قالب (فقط حروف انگلیسی کوچک، اعداد و -)"),
                        "description", Map.of("type", "string",
                                "description", "این قالب چه کاری انجام می‌دهد"),
                        "variables", Map.of("type", "array",
                                "description", "متغیرهای قالب: هر کدام {name, label, type (STRING|INT|PORT|BOOLEAN|SECRET), required}",
                                "items", Map.of("type", "object")),
                        "scriptText", Map.of("type", "string",
                                "description", "کد Groovy قالب، به همان سبک قالب‌های موجود کاتالوگ")
                ),
                "required", new String[]{"name", "description", "variables", "scriptText"}
        );
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        String description = (String) arguments.get("description");
        String script = (String) arguments.get("scriptText");
        Object rawVariables = arguments.get("variables");

        List<String> problems = new ArrayList<>();
        if (name == null || !NAME.matcher(name).matches()) {
            problems.add("نام فقط می‌تواند شامل حروف انگلیسی کوچک، اعداد و - باشد");
        } else if (templateRepository.findByNameAndDeletedFalse(name).isPresent()) {
            problems.add("قالبی با نام " + name + " از قبل وجود دارد");
        }
        if (script == null || script.isBlank()) {
            return Map.of("error", true, "message", "scriptText الزامی است");
        }

        List<String> storage = DataStoragePolicy.violations("scriptText", script);
        if (!storage.isEmpty()) {
            return Map.of("error", true, "message", DataStoragePolicy.message(storage));
        }

        List<GroovyTemplate.VariableMetadata> variables = new ArrayList<>();
        if (!(rawVariables instanceof List<?> list)) {
            problems.add("variables باید فهرستی از متغیرها باشد");
        } else {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m) || !(m.get("name") instanceof String varName)) {
                    problems.add("هر متغیر باید name داشته باشد: " + o);
                    continue;
                }
                VariableType type;
                try {
                    type = VariableType.valueOf(String.valueOf(m.get("type") == null ? "STRING" : m.get("type")).toUpperCase());
                } catch (IllegalArgumentException e) {
                    problems.add("نوع نامعتبر برای " + varName + ": " + m.get("type"));
                    continue;
                }
                // gwPort/gwPath are read by the ESB (it owns the listener), not by the script itself
                if (!List.of("gwPort", "gwPath").contains(varName)
                        && !Pattern.compile("\\b" + Pattern.quote(varName) + "\\b").matcher(script).find()) {
                    problems.add("متغیر " + varName + " تعریف شده ولی در کد استفاده نشده");
                }
                variables.add(new GroovyTemplate.VariableMetadata(varName,
                        m.get("label") == null ? varName : String.valueOf(m.get("label")), null, type,
                        !Boolean.FALSE.equals(m.get("required")), null, null));
            }
            for (String gateway : List.of("gwPort", "gwPath")) {
                if (variables.stream().noneMatch(v -> v.getName().equals(gateway))) {
                    problems.add("متغیر " + gateway + " الزامی است (گذرگاه روی آن منتشر می‌شود)");
                }
            }
        }

        // the same consumer check as every catalog template: identify by API key, then require a grant
        if (!script.contains("accessService") || !script.contains("getClientIdByApiKey") || !script.contains("hasAccess")) {
            problems.add("قالب باید مانند قالب‌های کاتالوگ، مصرف‌کننده را با X-API-Key شناسایی کند "
                    + "(accessService.getClientIdByApiKey) و دسترسی او را بررسی کند (hasAccess)");
        }

        String syntax = syntaxError(script);
        if (syntax != null) {
            problems.add("خطای نحوی Groovy: " + syntax);
        }

        if (!problems.isEmpty()) {
            return Map.of("error", true, "message", String.join("؛ ", problems));
        }

        String trialNote;
        if (trial.configured()) {
            Map<String, Object> verdict = trial.trial(script, trialValues(variables));
            if (verdict == null || !Boolean.TRUE.equals(verdict.get("ok"))) {
                return Map.of("error", true, "message", "اجرای آزمایشی قالب روی گذرگاه ناموفق بود (مرحلهٔ "
                        + (verdict == null ? "?" : verdict.get("stage")) + "): "
                        + (verdict == null ? "پاسخی نیامد" : verdict.get("error"))
                        + " — کد را اصلاح کن و دوباره پیشنهاد بده");
            }
            trialNote = "اجرای آزمایشی روی گذرگاه موفق بود";
        } else {
            trialNote = "گذرگاه آزمایشی پیکربندی نشده؛ قالب بدون اجرای آزمایشی ثبت شد";
        }

        GroovyTemplate saved = templateRepository.save(GroovyTemplate.builder()
                .name(name)
                .description(description)
                .scriptText(script)
                .variables(variables)
                .modelAuthored(true)
                .build());
        return Map.of(
                "success", true,
                "templateId", saved.getId(),
                "name", saved.getName(),
                "variables", variables.stream().map(GroovyTemplate.VariableMetadata::getName).toList(),
                "trial", trialNote,
                "message", "قالب " + saved.getName() + " به کاتالوگ افزوده شد؛ حالا سرویس را با service_groovy_config از آن بساز"
        );
    }

    /**
     * Values for the trial run: every address-like variable points at the sandbox's stand-in
     * provider, the others get a plausible value of their type.
     */
    static Map<String, Object> trialValues(List<GroovyTemplate.VariableMetadata> variables) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (GroovyTemplate.VariableMetadata v : variables) {
            String n = v.getName().toLowerCase();
            Object value;
            if (n.equals("gwpath")) {
                value = "/trial";
            } else if (n.contains("address") || n.contains("url") || n.contains("uri") || n.contains("endpoint")) {
                value = EsbTemplateTrial.PROVIDER + "/api/trial";
            } else {
                value = switch (v.getType()) {
                    case PORT -> "18999";
                    case INT -> "1000";
                    case BOOLEAN -> "true";
                    default -> "trial-" + v.getName();
                };
            }
            values.put(v.getName(), value);
        }
        return values;
    }

    /** Parses (does not run or link) the script; null if it is syntactically valid Groovy. */
    static String syntaxError(String script) {
        CompilationUnit unit = new CompilationUnit();
        unit.addSource("ProposedTemplate.groovy", script);
        try {
            unit.compile(Phases.CONVERSION);
            return null;
        } catch (CompilationFailedException e) {
            String msg = e.getMessage();
            return msg.length() > 400 ? msg.substring(0, 400) : msg;
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }
}
