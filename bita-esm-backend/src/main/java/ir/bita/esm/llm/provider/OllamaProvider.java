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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Ollama local LLM provider implementation.
 */
@Component
@ConditionalOnProperty(name = "app.llm.ollama.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class OllamaProvider implements LlmProvider {

    private static final String[] SUPPORTED_MODELS = {"llama3", "llama3:70b", "mistral", "mixtral"};

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${app.llm.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${app.llm.ollama.model:llama3}")
    private String defaultModel;

    @Override
    public String getName() {
        return "ollama";
    }

    @Override
    public String[] getSupportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public boolean supportsModel(String model) {
        // Ollama supports any model that's pulled
        return true;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        try {
            String url = ollamaUrl + "/api/chat";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = buildRequestBody(request);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("Ollama API error: {}", e.getMessage());
            throw new RuntimeException("Failed to call Ollama API", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String url = ollamaUrl + "/api/tags";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Ollama not available: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildRequestBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : defaultModel);
        body.put("stream", false);

        List<Map<String, Object>> messages = request.getMessages().stream()
                .map(m -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("role", m.getRole());
                    msg.put("content", m.getContent());
                    return msg;
                })
                .collect(Collectors.toList());
        body.put("messages", messages);

        // Ollama options
        Map<String, Object> options = new HashMap<>();
        if (request.getTemperature() != null) {
            options.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            options.put("num_predict", request.getMaxTokens());
        }
        if (!options.isEmpty()) {
            body.put("options", options);
        }

        // Tool support (Ollama 0.2+ with function calling models)
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = request.getTools().stream()
                    .map(t -> Map.<String, Object>of(
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

        return body;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseResponse(Map<String, Object> response) {
        Map<String, Object> message = (Map<String, Object>) response.get("message");

        LlmResponse.Message.MessageBuilder msgBuilder = LlmResponse.Message.builder()
                .role((String) message.get("role"))
                .content((String) message.get("content"));

        // Handle tool calls
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<LlmResponse.ToolCall> parsedCalls = toolCalls.stream()
                    .map(tc -> {
                        Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                        String args = fn.get("arguments") instanceof String 
                                ? (String) fn.get("arguments") 
                                : fn.get("arguments").toString();
                        return LlmResponse.ToolCall.builder()
                                .id(UUID.randomUUID().toString())
                                .type("function")
                                .function(LlmResponse.FunctionCall.builder()
                                        .name((String) fn.get("name"))
                                        .arguments(args)
                                        .build())
                                .build();
                    })
                    .collect(Collectors.toList());
            msgBuilder.toolCalls(parsedCalls);
        }

        return LlmResponse.builder()
                .id(UUID.randomUUID().toString())
                .model((String) response.get("model"))
                .choices(List.of(LlmResponse.Choice.builder()
                        .index(0)
                        .message(msgBuilder.build())
                        .finishReason("stop")
                        .build()))
                .build();
    }
}
