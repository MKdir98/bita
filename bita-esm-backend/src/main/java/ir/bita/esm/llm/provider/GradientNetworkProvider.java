package ir.bita.esm.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esm.llm.dto.LlmRequest;
import ir.bita.esm.llm.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;

/**
 * Gradient Network free LLM provider implementation.
 * Direct API access without any setup required.
 * 
 * API Endpoint: https://chat.gradient.network/api/generate
 * 
 * Supported models:
 * - GPT OSS 120B: Powerful open-source model (default)
 * - Qwen3 235B: Large Qwen model
 * 
 * No setup required - works out of the box!
 * 
 * @see <a href="https://chat.gradient.network">Gradient Network</a>
 */
@Component
@ConditionalOnProperty(name = "app.llm.gradient.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class GradientNetworkProvider implements LlmProvider {

    /**
     * Gradient Network API endpoint.
     */
    private static final String API_URL = "https://chat.gradient.network/api/generate";
    
    /**
     * Default model to use.
     */
    private static final String DEFAULT_MODEL = "GPT OSS 120B";

    /**
     * Supported models.
     */
    private static final String[] SUPPORTED_MODELS = {
            "GPT OSS 120B",
            "Qwen3 235B"
    };

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Override
    public String getName() {
        return "gradient";
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
            headers.set("Accept", "application/x-ndjson");
            headers.set("Origin", "https://chat.gradient.network");
            headers.set("Referer", "https://chat.gradient.network/");
            headers.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36");

            Map<String, Object> body = buildRequestBody(request);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            log.debug("Calling Gradient Network API");
            
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL, HttpMethod.POST, entity, String.class);

            return parseNdjsonResponse(response.getBody(), request);
        } catch (Exception e) {
            log.error("Gradient Network API error: {}", e.getMessage());
            throw new RuntimeException("Failed to call Gradient Network API", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private Map<String, Object> buildRequestBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();
        
        String model = request.getModel() != null ? request.getModel() : DEFAULT_MODEL;
        // Map model aliases
        if ("gpt-oss-120b".equalsIgnoreCase(model) || "gpt-oss".equalsIgnoreCase(model)) {
            model = "GPT OSS 120B";
        } else if ("qwen-3-235b".equalsIgnoreCase(model) || "qwen3-235b".equalsIgnoreCase(model)) {
            model = "Qwen3 235B";
        }
        
        body.put("model", model);
        body.put("clusterMode", model.contains("GPT") ? "nvidia" : "hybrid");
        body.put("enableThinking", false);
        
        // Convert messages
        List<Map<String, String>> messages = new ArrayList<>();
        for (LlmRequest.Message msg : request.getMessages()) {
            Map<String, String> message = new HashMap<>();
            message.put("role", msg.getRole().toLowerCase());
            message.put("content", msg.getContent());
            messages.add(message);
        }
        body.put("messages", messages);

        return body;
    }

    private LlmResponse parseNdjsonResponse(String ndjsonResponse, LlmRequest request) {
        StringBuilder contentBuilder = new StringBuilder();
        boolean inFinalChannel = false;
        
        try (BufferedReader reader = new BufferedReader(new StringReader(ndjsonResponse))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                
                try {
                    JsonNode node = objectMapper.readTree(line);
                    String type = node.path("type").asText();
                    
                    if ("reply".equals(type)) {
                        JsonNode data = node.path("data");
                        String content = data.path("content").asText();
                        
                        // Track channel markers to only capture final response
                        if (content.contains("<|channel|>")) {
                            // Check if next content is "final"
                            continue;
                        }
                        if ("final".equals(content)) {
                            inFinalChannel = true;
                            continue;
                        }
                        if ("analysis".equals(content)) {
                            inFinalChannel = false;
                            continue;
                        }
                        
                        // Skip control tokens
                        if (content.startsWith("<|") && content.endsWith("|>")) {
                            if ("<|message|>".equals(content)) {
                                // Message content follows
                            }
                            continue;
                        }
                        
                        // Only capture content when in final channel
                        if (inFinalChannel && !content.isEmpty()) {
                            contentBuilder.append(content);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse NDJSON line: {}", line);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing NDJSON response: {}", e.getMessage());
        }
        
        String content = contentBuilder.toString().trim();
        
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
                .id("gradient-" + System.currentTimeMillis())
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
