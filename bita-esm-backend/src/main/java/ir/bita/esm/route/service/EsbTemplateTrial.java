package ir.bita.esm.route.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Asks the sandbox ESB (an ESB instance that runs no service) to trial-run a proposed template
 * with its own libraries: compile, start, and call it in front of a stand-in provider. ESM itself
 * has none of the ESB's runtime libraries, so this is where a missing class or a method called
 * with the wrong arguments is found — before the template is registered, not in production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EsbTemplateTrial {

    /** Placeholder the sandbox replaces with its stand-in provider's address. */
    public static final String PROVIDER = "${TRIAL_PROVIDER}";

    private final RestTemplate restTemplate;

    @Value("${app.esb.sandbox-url:}")
    private String sandboxUrl;

    public boolean configured() {
        return sandboxUrl != null && !sandboxUrl.isBlank();
    }

    /** The sandbox's verdict: {"ok": true} or {"ok": false, "stage": ..., "error": ...}. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> trial(String script, Map<String, Object> variables) {
        Map<String, Object> verdict = restTemplate.postForObject(sandboxUrl + "/internal/trial-script",
                Map.of("script", script, "variables", variables), Map.class);
        log.info("Template trial on {}: {}", sandboxUrl, verdict);
        return verdict;
    }
}
