package ir.bita.esm.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from LLM provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {
    private String id;
    private String model;
    private List<Choice> choices;
    private Usage usage;
    private String finishReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private int index;
        private Message message;
        private String finishReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private List<ToolCall> toolCalls;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    public boolean hasToolCalls() {
        return choices != null && !choices.isEmpty() 
                && choices.get(0).getMessage() != null 
                && choices.get(0).getMessage().getToolCalls() != null
                && !choices.get(0).getMessage().getToolCalls().isEmpty();
    }

    public String getContent() {
        if (choices == null || choices.isEmpty()) return null;
        Message msg = choices.get(0).getMessage();
        return msg != null ? msg.getContent() : null;
    }

    public List<ToolCall> getToolCalls() {
        if (choices == null || choices.isEmpty()) return List.of();
        Message msg = choices.get(0).getMessage();
        return msg != null && msg.getToolCalls() != null ? msg.getToolCalls() : List.of();
    }
}
