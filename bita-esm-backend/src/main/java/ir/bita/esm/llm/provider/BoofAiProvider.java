package ir.bita.esm.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esm.llm.dto.LlmRequest;
import ir.bita.esm.llm.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BoofAI (Shaboof) - Iranian LLM provider.
 * OpenAI-compatible API.
 * 
 * API Endpoint: https://chat.boofai.com/api/chat/completions
 * Model: shaboof-model (Persian LLM)
 * 
 * @see <a href="https://chat.boofai.com">BoofAI</a>
 */
@Component
@ConditionalOnProperty(name = "app.llm.boofai.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BoofAiProvider implements LlmProvider {

    private static final String API_URL = "https://chat.boofai.com/api/chat/completions";
    private static final String DEFAULT_MODEL = "shaboof-model";
    private static final String AUTH_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjZkZTJkNmJiLTgzYTItNDU1ZC04YjY2LTEzNTMzYTAwMjgwZSIsImV4cCI6MTc3MzQxOTExOX0.Lla8KPqv1SKO-ICwPS7_N1JMSP1nsk2lY6-SPm8gN-0";

    private static final String[] SUPPORTED_MODELS = {"shaboof-model", "shaboof2"};

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Override
    public String getName() {
        return "boofai";
    }

    @Override
    public String[] getSupportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public boolean supportsModel(String model) {
        if (model == null) return true;
        for (String supported : SUPPORTED_MODELS) {
            if (supported.equalsIgnoreCase(model)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + AUTH_TOKEN);
            headers.set("Origin", "https://chat.boofai.com");
            headers.set("Referer", "https://chat.boofai.com/");

            Map<String, Object> body = buildRequestBody(request);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            log.debug("Calling BoofAI API");

            ResponseEntity<Map> response = restTemplate.exchange(
                    API_URL, HttpMethod.POST, entity, Map.class);

            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("BoofAI API error: {}", e.getMessage());
            throw new RuntimeException("Failed to call BoofAI API", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();

        body.put("model", DEFAULT_MODEL);
        body.put("stream", false);

        List<Map<String, String>> messages = request.getMessages().stream()
                .map(m -> {
                    Map<String, String> msg = new HashMap<>();
                    msg.put("role", m.getRole().toLowerCase());
                    msg.put("content", m.getContent());
                    return msg;
                })
                .collect(Collectors.toList());
        body.put("messages", messages);

        // Optional parameters
        body.put("params", new HashMap<>());
        body.put("tool_servers", new ArrayList<>());
        
        Map<String, Object> features = new HashMap<>();
        features.put("image_generation", false);
        features.put("code_interpreter", false);
        features.put("web_search", false);
        body.put("features", features);

        return body;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseResponse(Map<String, Object> response) {
        LlmResponse.LlmResponseBuilder builder = LlmResponse.builder()
                .id((String) response.get("id"))
                .model((String) response.get("model"));

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            List<LlmResponse.Choice> parsedChoices = choices.stream()
                    .map(this::parseChoice)
                    .collect(Collectors.toList());
            builder.choices(parsedChoices);
        }

        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        if (usage != null) {
            builder.usage(LlmResponse.Usage.builder()
                    .promptTokens(getIntValue(usage, "prompt_tokens"))
                    .completionTokens(getIntValue(usage, "completion_tokens"))
                    .totalTokens(getIntValue(usage, "total_tokens"))
                    .build());
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private LlmResponse.Choice parseChoice(Map<String, Object> choice) {
        LlmResponse.Choice.ChoiceBuilder builder = LlmResponse.Choice.builder()
                .index(getIntValue(choice, "index"))
                .finishReason((String) choice.get("finish_reason"));

        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        if (message != null) {
            LlmResponse.Message.MessageBuilder msgBuilder = LlmResponse.Message.builder()
                    .role((String) message.get("role"))
                    .content((String) message.get("content"));
            builder.message(msgBuilder.build());
        }

        return builder.build();
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
