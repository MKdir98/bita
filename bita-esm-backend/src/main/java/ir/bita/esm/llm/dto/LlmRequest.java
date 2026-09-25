package ir.bita.esm.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request to LLM provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {
    private String model;
    private List<Message> messages;
    private List<Tool> tools;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> responseFormat;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private List<ToolCall> toolCalls;
        private String toolCallId;
        /** For role "tool": the tool that produced this result (required by stricter chat templates). */
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {
        private String type;
        private Function function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type;
        private FunctionCall function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private String arguments;
    }
}
