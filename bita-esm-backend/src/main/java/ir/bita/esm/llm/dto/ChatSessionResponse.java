package ir.bita.esm.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionResponse {
    private Long id;
    private String title;
    private String modelName;
    private boolean active;
    private int messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ChatMessageResponse> messages;
}
