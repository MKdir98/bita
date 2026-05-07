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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Startnest AI provider implementation.
 * Uses the Startnest API for chat completions.
 * 
 * Startnest provides access to GPT-4o-mini model.
 * 
 * @see <a href="https://play.google.com/store/apps/details?id=starnest.aitype.aikeyboard.chatbot.chatgpt">Startnest App</a>
 */
@Component
@ConditionalOnProperty(name = "app.llm.startnest.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class StartnestProvider implements LlmProvider {

    private static final String API_URL = "https://api.startnest.uk/api/completions/stream";
    private static final String KID = "36ccfe00-78fc-4cab-9c5b-5460b0c78513";
    private static final String ALGORITHM = "sha256";
    private static final int VALIDITY = 90;
    
    /**
     * Supported models through Startnest.
     */
    private static final String[] SUPPORTED_MODELS = {
            "gpt-4o-mini"
    };

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Override
    public String getName() {
        return "startnest";
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

    /**
     * Generate signature for authorization header.
     * 
     * @param timestamp Unix timestamp in seconds
     * @return Signature string for Authorization header
     */
    private String generateSignature(long timestamp) {
        String userId = "";
        String signatureInput = KID + timestamp + VALIDITY;
        String signatureValue = sha256Hash(signatureInput);
        
        return String.format(
                "Signature kid=%s&algorithm=%s&timestamp=%d&validity=%d&userId=%s&value=%s",
                KID, ALGORITHM, timestamp, VALIDITY, userId, signatureValue
        );
    }

    /**
     * Calculate SHA-256 hash of input string.
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept-Encoding", "gzip");
            headers.set("app_name", "AIKEYBOARD");
            headers.set("Authorization", generateSignature(timestamp));
            headers.set("Connection", "Keep-Alive");
            headers.set("Host", "api.startnest.uk");
            headers.set("User-Agent", "okhttp/4.9.0");

            Map<String, Object> body = buildRequestBody(request);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            log.debug("Calling Startnest API with model: gpt-4o-mini");
            
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL, HttpMethod.POST, entity, String.class);

            return parseStreamResponse(response.getBody());
        } catch (Exception e) {
            log.error("Startnest API error: {}", e.getMessage());
            throw new RuntimeException("Failed to call Startnest API", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(LlmRequest request) {
        Map<String, Object> body = new HashMap<>();
        
        body.put("isVip", true);
        body.put("stream", false); // Using non-streaming for simplicity
        
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }

        // Build messages array with Startnest format
        List<Map<String, Object>> messages = request.getMessages().stream()
                .map(m -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("role", m.getRole());
                    
                    // Startnest requires content as array of objects
                    List<Map<String, Object>> contentArray = new ArrayList<>();
                    
                    if (m.getContent() != null && !m.getContent().isEmpty()) {
                        Map<String, Object> textContent = new HashMap<>();
                        textContent.put("type", "text");
                        textContent.put("text", m.getContent());
                        contentArray.add(textContent);
                    }
                    
                    msg.put("content", contentArray);
                    return msg;
                })
                .collect(Collectors.toList());
        
        body.put("messages", messages);

        return body;
    }

    /**
     * Parse SSE stream response from Startnest API.
     */
    @SuppressWarnings("unchecked")
    private LlmResponse parseStreamResponse(String responseText) {
        StringBuilder fullContent = new StringBuilder();
        String finishReason = null;
        String id = null;
        String model = "gpt-4o-mini";
        
        String[] lines = responseText.split("\n");
        for (String line : lines) {
            if (line.startsWith("data: ")) {
                String dataStr = line.substring(6).trim();
                if (dataStr.equals("[DONE]")) {
                    break;
                }
                try {
                    Map<String, Object> jsonData = objectMapper.readValue(dataStr, Map.class);
                    
                    if (id == null && jsonData.containsKey("id")) {
                        id = (String) jsonData.get("id");
                    }
                    
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) jsonData.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        
                        // Handle streaming delta
                        Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                        if (delta != null) {
                            String content = (String) delta.get("content");
                            if (content != null) {
                                fullContent.append(content);
                            }
                        }
                        
                        // Handle non-streaming message
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        if (message != null) {
                            String content = (String) message.get("content");
                            if (content != null) {
                                fullContent.append(content);
                            }
                        }
                        
                        // Store finish_reason
                        Object fr = choice.get("finish_reason");
                        if (fr != null) {
                            finishReason = fr.toString();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse SSE line: {}", dataStr);
                }
            }
        }
        
        // Try parsing as regular JSON if SSE parsing didn't work
        if (fullContent.length() == 0) {
            try {
                Map<String, Object> jsonData = objectMapper.readValue(responseText, Map.class);
                return parseJsonResponse(jsonData);
            } catch (Exception e) {
                log.debug("Failed to parse as JSON: {}", e.getMessage());
            }
        }
        
        // Build response
        return LlmResponse.builder()
                .id(id != null ? id : "startnest-" + System.currentTimeMillis())
                .model(model)
                .choices(List.of(
                        LlmResponse.Choice.builder()
                                .index(0)
                                .finishReason(finishReason != null ? finishReason : "stop")
                                .message(LlmResponse.Message.builder()
                                        .role("assistant")
                                        .content(fullContent.toString().trim())
                                        .build())
                                .build()
                ))
                .build();
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseJsonResponse(Map<String, Object> response) {
        LlmResponse.LlmResponseBuilder builder = LlmResponse.builder()
                .id((String) response.get("id"))
                .model((String) response.getOrDefault("model", "gpt-4o-mini"));

        // Parse choices
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            List<LlmResponse.Choice> parsedChoices = choices.stream()
                    .map(this::parseChoice)
                    .collect(Collectors.toList());
            builder.choices(parsedChoices);
        }

        // Parse usage statistics
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

            // Parse tool calls if present
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
