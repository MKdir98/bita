package ir.bita.esm.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response from LLM in action-based format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResponse {
    private String action;
    private Map<String, Object> params;
}
