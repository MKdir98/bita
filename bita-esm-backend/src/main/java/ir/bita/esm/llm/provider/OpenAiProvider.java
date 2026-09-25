package ir.bita.esm.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esm.llm.dto.LlmRequest;
import ir.bita.esm.llm.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAI API provider implementation.
 */
@Component
@ConditionalOnProperty(name = "app.llm.openai.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OpenAiProvider implements LlmProvider {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String[] SUPPORTED_MODELS = {"gpt-4", "gpt-4-turbo", "gpt-3.5-turbo"};

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${app.llm.openai.api-key:}")
    private String apiKey;

    @Override
    public String getName() {
        return "openai";
    }

    @Override
    public String[] getSupportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public boolean supportsModel(String model) {
        for (String m : SUPPORTED_MODELS) {
            if (m.equals(model)) return true;
        }
        return false;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = buildRequestBody(request);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_URL, HttpMethod.POST, entity, Map.class);

            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("OpenAI API error: {}", e.getMessage());
            throw new RuntimeException("Failed to call OpenAI API", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : "gpt-4");

        List<Map<String, Object>> messages = request.getMessages().stream()
                .map(m -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("role", m.getRole());
                    msg.put("content", m.getContent());
                    if (m.getToolCalls() != null) {
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
                    if (m.getName() != null) {
                        msg.put("name", m.getName());
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
                    .promptTokens(((Number) usage.get("prompt_tokens")).intValue())
                    .completionTokens(((Number) usage.get("completion_tokens")).intValue())
                    .totalTokens(((Number) usage.get("total_tokens")).intValue())
                    .build());
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private LlmResponse.Choice parseChoice(Map<String, Object> choice) {
        LlmResponse.Choice.ChoiceBuilder builder = LlmResponse.Choice.builder()
                .index(((Number) choice.get("index")).intValue())
                .finishReason((String) choice.get("finish_reason"));

        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        if (message != null) {
            LlmResponse.Message.MessageBuilder msgBuilder = LlmResponse.Message.builder()
                    .role((String) message.get("role"))
                    .content((String) message.get("content"));

            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null) {
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
}
