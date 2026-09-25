package ir.bita.esm.llm.dto;

import ir.bita.esm.llm.entity.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private MessageRole role;
    private String content;
    private List<ToolCallResponse> toolCalls;
    private ToolCallResponse pendingToolExecution;
    private LocalDateTime createdAt;
    /** The model's reply broke the action format and was re-sent once after being told why. */
    private boolean formatRepaired;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallResponse {
        private String id;
        private String toolName;
        private Map<String, Object> arguments;
        private String status;
        private boolean requiresConfirmation;
        private Map<String, Object> result;
        private String errorMessage;
    }
}
