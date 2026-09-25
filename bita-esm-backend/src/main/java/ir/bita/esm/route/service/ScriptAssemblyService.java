package ir.bita.esm.route.service;

import ir.bita.common.domain.VariableType;
import ir.bita.esm.route.entity.ComponentTemplate;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.repository.ComponentTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assembles final Groovy scripts by inlining ComponentTemplate code for each #componentName reference.
 * Also scans ${varName} placeholders to derive the variable list for a template.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ScriptAssemblyService {

    private static final Pattern COMPONENT_REF = Pattern.compile("#([\\w-]+)");
    private static final Pattern VAR_REF = Pattern.compile("\\$\\{([\\w]+)}");

    private final ComponentTemplateRepository componentTemplateRepository;

    /**
     * Assembles the final Groovy script for evaluation on the ESB.
     * For each #componentName in the template, the matching ComponentTemplate.groovyCode
     * is inlined at the top of the script.
     */
    public String assemble(GroovyTemplate template) {
        String scriptText = template.getScriptText();
        StringBuilder assembled = new StringBuilder();

        Matcher m = COMPONENT_REF.matcher(scriptText);
        while (m.find()) {
            String componentName = m.group(1);
            componentTemplateRepository.findByNameAndDeletedFalse(componentName)
                    .ifPresentOrElse(
                            ct -> {
                                if (ct.getGroovyCode() != null && !ct.getGroovyCode().isBlank()) {
                                    assembled.append("// ── inlined from ComponentTemplate: ")
                                             .append(componentName).append(" ──\n")
                                             .append(ct.getGroovyCode()).append("\n\n");
                                    log.debug("Inlined component: {}", componentName);
                                } else {
                                    log.warn("ComponentTemplate '{}' has no groovyCode — skipping inline", componentName);
                                }
                            },
                            () -> log.warn("ComponentTemplate '{}' not found — skipping inline", componentName)
                    );
        }

        assembled.append("// ── route from GroovyTemplate: ").append(template.getName()).append(" ──\n");
        assembled.append(scriptText);

        return assembled.toString();
    }

    /**
     * Scans ${varName} placeholders from a script text and returns the list of variable names found.
     */
    public List<String> scanVariableNames(String scriptText) {
        List<String> names = new ArrayList<>();
        Matcher m = VAR_REF.matcher(scriptText);
        while (m.find()) {
            String name = m.group(1);
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Produces a default variable metadata list from ${varName} scan.
     * Type defaults to STRING; label defaults to the variable name.
     */
    public List<GroovyTemplate.VariableMetadata> scanVariables(String scriptText) {
        List<GroovyTemplate.VariableMetadata> vars = new ArrayList<>();
        for (String name : scanVariableNames(scriptText)) {
            GroovyTemplate.VariableMetadata meta = new GroovyTemplate.VariableMetadata();
            meta.setName(name);
            meta.setLabel(name);
            meta.setType(VariableType.STRING);
            meta.setRequired(true);
            vars.add(meta);
        }
        return vars;
    }
}
