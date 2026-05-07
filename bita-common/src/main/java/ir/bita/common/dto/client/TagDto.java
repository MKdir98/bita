package ir.bita.common.dto.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a tag with key-value pair.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagDto {

    private Long id;

    @NotBlank(message = "Tag key is required")
    @Size(max = 100, message = "Tag key cannot exceed 100 characters")
    private String key;

    @NotBlank(message = "Tag value is required")
    @Size(max = 500, message = "Tag value cannot exceed 500 characters")
    private String value;
}
