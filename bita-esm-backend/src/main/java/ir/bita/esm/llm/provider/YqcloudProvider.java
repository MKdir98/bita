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
 * Yqcloud free LLM provider implementation.
 * Direct API access without g4f server.
 * 
 * API Endpoint: https://api.binjie.fun/api/generateStream
 * 
 * Supported models:
 * - gpt-4: Main model (free, no auth required)
 * 
 * No setup required - works out of the box!
 * 
 * @see <a href="https://chat9.yqcloud.top">Yqcloud Chat</a>
 */
@Component
@ConditionalOnProperty(name = "app.llm.yqcloud.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class YqcloudProvider implements LlmProvider {

    /**
     * Yqcloud API endpoint.
     */
    private static final String API_URL = "https://api.binjie.fun/api/generateStream";
    
    /**
     * Default model to use.
     */
    private static final String DEFAULT_MODEL = "gpt-4";

    /**
     * Supported models.
     */
    private static final String[] SUPPORTED_MODELS = {
            "gpt-4"
    };

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Override
    public String getName() {
        return "yqcloud";
    }

    @Override
    public String[] getSupportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public boolean supportsModel(String model) {
        if (model == null) return true; // Use default
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
            headers.set("Origin", "https://chat9.yqcloud.top");
            headers.set("Referer", "https://chat9.yqcloud.top/");
            headers.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

            Map<String, Object> body = buildRequestBody(request);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            log.debug("Calling Yqcloud API");
            
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL, HttpMethod.POST, entity, String.class);

            return parseResponse(response.getBody(), request);
        } catch (Exception e) {
            log.error("Yqcloud API error: {}", e.getMessage());
            throw new RuntimeException("Failed to call Yqcloud API", e);
        }
    }

    @Override
    public boolean isAvailable() {
        // Yqcloud is always available (free public API)
        return true;
    }

    private Map<String, Object> buildRequestBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();
        
        // Extract system message
        String systemMessage = "";
        StringBuilder promptBuilder = new StringBuilder();
        
        for (LlmRequest.Message msg : request.getMessages()) {
            if ("system".equals(msg.getRole())) {
                systemMessage = msg.getContent();
            } else if ("user".equals(msg.getRole())) {
                if (promptBuilder.length() > 0) {
                    promptBuilder.append("\n\n");
                }
                promptBuilder.append("User: ").append(msg.getContent());
            } else if ("assistant".equals(msg.getRole())) {
                if (promptBuilder.length() > 0) {
                    promptBuilder.append("\n\n");
                }
                promptBuilder.append("Assistant: ").append(msg.getContent());
            }
        }
        
        // Add final prompt marker
        if (promptBuilder.length() > 0) {
            promptBuilder.append("\n\nAssistant: ");
        }
        
        body.put("prompt", promptBuilder.toString());
        body.put("userId", "#/chat/" + System.currentTimeMillis());
        body.put("network", true);
        body.put("system", systemMessage);
        body.put("withoutContext", false);
        body.put("stream", false);

        return body;
    }

    private LlmResponse parseResponse(String responseText, LlmRequest request) {
        // Yqcloud returns plain text response
        String content = responseText != null ? responseText.trim() : "";
        
        LlmResponse.Message message = LlmResponse.Message.builder()
                .role("assistant")
                .content(content)
                .build();
        
        LlmResponse.Choice choice = LlmResponse.Choice.builder()
                .index(0)
                .message(message)
                .finishReason("stop")
                .build();
        
        return LlmResponse.builder()
                .id("yqcloud-" + System.currentTimeMillis())
                .model(DEFAULT_MODEL)
                .choices(Collections.singletonList(choice))
                .usage(LlmResponse.Usage.builder()
                        .promptTokens(0)
                        .completionTokens(0)
                        .totalTokens(0)
                        .build())
                .build();
    }
}
