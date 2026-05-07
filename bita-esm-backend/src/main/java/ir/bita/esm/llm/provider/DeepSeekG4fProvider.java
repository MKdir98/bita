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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DeepSeek provider implementation via Pollinations AI.
 * Uses the Pollinations AI free OpenAI-compatible API.
 * 
 * API Endpoint: https://text.pollinations.ai/openai
 * 
 * Supported models (verified working 2026-02-05):
 * - deepseek-r1: DeepSeek R1 reasoning model
 * - r1-1776: DeepSeek R1 variant
 * 
 * No setup required - works out of the box!
 * 
 * @see <a href="https://pollinations.ai">Pollinations AI</a>
 * @see <a href="https://www.deepseek.com/">DeepSeek</a>
 */
@Component
@ConditionalOnProperty(name = "app.llm.deepseek.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DeepSeekG4fProvider implements LlmProvider {

    /**
     * Pollinations AI text API endpoint (OpenAI-compatible).
     */
    private static final String API_URL = "https://text.pollinations.ai/openai";
    
    /**
     * Default model to use.
     */
    private static final String DEFAULT_MODEL = "deepseek-r1";

    /**
     * Verified working DeepSeek models.
     */
    private static final String[] SUPPORTED_MODELS = {
            "deepseek-r1",  // DeepSeek R1 reasoning model
            "r1-1776"       // DeepSeek R1 variant
    };

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Override
    public String getName() {
        return "deepseek";
    }

    @Override
    public String[] getSupportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public boolean supportsModel(String model) {
        if (model == null) return false;
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

            Map<String, Object> body = buildRequestBody(request);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            String model = request.getModel() != null ? request.getModel() : DEFAULT_MODEL;
            log.debug("Calling DeepSeek via Pollinations AI with model: {}", model);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_URL, HttpMethod.POST, entity, Map.class);

            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("DeepSeek Pollinations API error: {}", e.getMessage());
            throw new RuntimeException("Failed to call DeepSeek API", e);
        }
    }

    @Override
    public boolean isAvailable() {
        // Pollinations AI is always available (free public API)
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();
        
        String model = request.getModel() != null ? request.getModel() : DEFAULT_MODEL;
        body.put("model", model);

        List<Map<String, Object>> messages = request.getMessages().stream()
                .map(m -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("role", m.getRole());
                    msg.put("content", m.getContent());
                    
                    if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                        msg.put("tool_calls", m.getToolCalls().stream()
                                .map(tc -> Map.of(
                                        "id", tc.getId(),
                                        "type", "function",
                                        "function", Map.of(
                                                "name", tc.getFunction().getName(),
                                                "arguments", tc.getFunction().getArguments()
                                        )
                                ))
                                .collect(Collectors.toList()));
                    }
                    
                    if (m.getToolCallId() != null) {
                        msg.put("tool_call_id", m.getToolCallId());
                    }
                    
                    return msg;
                })
                .collect(Collectors.toList());
        body.put("messages", messages);

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = request.getTools().stream()
                    .map(t -> Map.of(
                            "type", "function",
                            "function", Map.of(
                                    "name", t.getFunction().getName(),
                                    "description", t.getFunction().getDescription(),
                                    "parameters", t.getFunction().getParameters()
                            )
                    ))
                    .collect(Collectors.toList());
            body.put("tools", tools);
        }

        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }

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

            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                List<LlmResponse.ToolCall> parsedCalls = toolCalls.stream()
                        .map(tc -> {
                            Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                            return LlmResponse.ToolCall.builder()
                                    .id((String) tc.get("id"))
                                    .type((String) tc.get("type"))
                                    .function(LlmResponse.FunctionCall.builder()
                                            .name((String) fn.get("name"))
                                            .arguments((String) fn.get("arguments"))
                                            .build())
                                    .build();
                        })
                        .collect(Collectors.toList());
                msgBuilder.toolCalls(parsedCalls);
            }

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
