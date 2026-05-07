package ir.bita.esm.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Tag.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagResponse {

    private Long id;
    private String key;
    private String value;
}
