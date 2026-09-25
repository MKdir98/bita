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
 * FreeLLMAPI provider — an OpenAI-compatible router (https://freellmapi.app). Same request
 * and response shape as {@link OpenAiProvider}; only the base URL, auth header and the set
 * of accepted model names differ, so the wire format is duplicated rather than shared to
 * keep each provider a self-contained, independently reviewable unit (matches this
 * package's existing style — none of the other providers extend a common base class either).
 *
 * <p>Used to run the real ESM chat pipeline (ChatService → LlmProviderFactory →
 * this provider → FreeLLMAPI) against a live model in tests, instead of a mocked
 * provider — see GroovyLlmScenarioIT.
 */
@Component
@ConditionalOnProperty(name = "app.llm.freellmapi.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class FreeLlmApiProvider implements LlmProvider {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${app.llm.freellmapi.base-url:http://127.0.0.1:3001/v1}")
    private String baseUrl;

    @Value("${app.llm.freellmapi.api-key:}")
    private String apiKey;

    /** FreeLLMAPI's own router pseudo-model; it picks whichever upstream model is available. */
    @Value("${app.llm.freellmapi.default-model:auto}")
    private String defaultModel;

    @Override
    public String getName() {
        return "freellmapi";
    }

    @Override
    public String[] getSupportedModels() {
        // FreeLLMAPI fronts dozens of upstream models behind one OpenAI-compatible API;
        // "auto" and any model id it lists are all valid — see /v1/models.
        return new String[]{defaultModel};
    }

    @Override
    public boolean supportsModel(String model) {
        return true;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException("FreeLLMAPI key not configured (app.llm.freellmapi.api-key)");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = buildRequestBody(request);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/chat/completions", HttpMethod.POST, entity, Map.class);

            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("FreeLLMAPI error: {}", e.getMessage());
            throw new RuntimeException("Failed to call FreeLLMAPI", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel() : defaultModel);

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
        if (request.getResponseFormat() != null) {
            body.put("response_format", request.getResponseFormat());
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
            builder.choices(choices.stream().map(this::parseChoice).collect(Collectors.toList()));
        }

        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        if (usage != null) {
            builder.usage(LlmResponse.Usage.builder()
                    .promptTokens(numberOr0(usage.get("prompt_tokens")))
                    .completionTokens(numberOr0(usage.get("completion_tokens")))
                    .totalTokens(numberOr0(usage.get("total_tokens")))
                    .build());
        }

        return builder.build();
    }

    private int numberOr0(Object n) {
        return n instanceof Number num ? num.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse.Choice parseChoice(Map<String, Object> choice) {
        LlmResponse.Choice.ChoiceBuilder builder = LlmResponse.Choice.builder()
                .index(numberOr0(choice.get("index")))
                .finishReason((String) choice.get("finish_reason"));

        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        if (message != null) {
            LlmResponse.Message.MessageBuilder msgBuilder = LlmResponse.Message.builder()
                    .role((String) message.get("role"))
                    .content((String) message.get("content"));

            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null) {
                msgBuilder.toolCalls(toolCalls.stream().map(tc -> {
                    Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                    return LlmResponse.ToolCall.builder()
                            .id((String) tc.get("id"))
                            .type((String) tc.get("type"))
                            .function(LlmResponse.FunctionCall.builder()
                                    .name((String) fn.get("name"))
                                    .arguments((String) fn.get("arguments"))
                                    .build())
                            .build();
                }).collect(Collectors.toList()));
            }
            builder.message(msgBuilder.build());
        }
        return builder.build();
    }
}
