package ir.bita.esm.llm.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esm.llm.dto.ActionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Validator for LLM responses in action-based format.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmResponseValidator {

    private final ObjectMapper objectMapper;

    private static final List<String> VALID_ACTIONS = Arrays.asList(
            "ask_question",
            "request_data",
            "endpoint_template",
            "component_template",
            "route_template",
            "endpoint_instance",
            "component_instance",
            "route_instance",
            "complete"
    );

    /**
     * Validates and parses JSON response from LLM.
     *
     * @param jsonResponse JSON string from LLM
     * @return Parsed ActionResponse
     * @throws ValidationException if JSON is invalid or doesn't match schema
     */
    public ActionResponse validate(String jsonResponse) throws ValidationException {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            throw new ValidationException("خروجی خالی است");
        }

        try {
            String normalizedJson = extractJsonObject(jsonResponse);
            if (normalizedJson == null) {
                throw new ValidationException("خروجی JSON معتبر پیدا نشد");
            }

            // Parse JSON
            ActionResponse response = objectMapper.readValue(normalizedJson, ActionResponse.class);

            // Validate required fields
            if (response.getAction() == null || response.getAction().trim().isEmpty()) {
                throw new ValidationException("فیلد 'action' الزامی است");
            }

            if (response.getParams() == null) {
                throw new ValidationException("فیلد 'params' الزامی است");
            }

            // Validate action is in allowed list
            if (!VALID_ACTIONS.contains(response.getAction())) {
                throw new ValidationException("action نامعتبر است: " + response.getAction() +
                        ". action های مجاز: " + String.join(", ", VALID_ACTIONS));
            }

            log.debug("Validated action response: action={}, params={}", 
                    response.getAction(), response.getParams().keySet());

            return response;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON response: {}", jsonResponse, e);
            throw new ValidationException("خروجی JSON معتبر نیست: " + e.getMessage());
        }
    }

    /**
     * Checks if a string looks like valid JSON.
     */
    public boolean looksLikeJson(String content) {
        return extractJsonObject(content) != null;
    }

    /**
     * Extracts the first JSON object from raw model output.
     * Handles markdown code fences and extra text around JSON.
     */
    public String extractJsonObject(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        String normalized = stripCodeFence(content).trim();
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            return normalized;
        }

        int start = normalized.indexOf('{');
        if (start < 0) {
            return null;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = start; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (ch == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return normalized.substring(start, i + 1);
                }
            }
        }

        return null;
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }

        int lastFence = trimmed.lastIndexOf("```");
        if (lastFence <= firstNewline) {
            return trimmed.substring(firstNewline + 1);
        }

        return trimmed.substring(firstNewline + 1, lastFence);
    }
}
