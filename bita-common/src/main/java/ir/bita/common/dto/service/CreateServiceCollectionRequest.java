package ir.bita.common.dto.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new service collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceCollectionRequest {

    @NotBlank(message = "Collection name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Base path is required")
    @Pattern(regexp = "^/esb/[a-z][a-z0-9-]*$", message = "Base path must start with /esb/ followed by lowercase letters, numbers, and hyphens")
    private String basePath;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}
