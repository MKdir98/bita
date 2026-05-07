package ir.bita.esm.llm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmToolRequest {
    @NotBlank(message = "Tool call ID is required")
    private String toolCallId;
    private boolean confirmed;
}
