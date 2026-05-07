package ir.bita.common.dto.service;

import ir.bita.common.domain.ServicePhase;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceRequest {

    @NotNull(message = "Collection ID is required")
    @Positive(message = "Collection ID must be positive")
    private Long collectionId;

    @NotBlank(message = "Service name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @NotBlank(message = "Version is required")
    @Pattern(regexp = "^\\d+\\.\\d+(\\.\\d+)?$", message = "Version must follow semantic versioning (e.g., 1.0, 1.0.0)")
    private String version;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    /**
     * Initial phase for the service. Defaults to DRAFT if not provided.
     */
    private ServicePhase phase;

    @Min(value = 1, message = "Minimum replicas must be at least 1")
    @Max(value = 100, message = "Minimum replicas cannot exceed 100")
    private Integer minReplicas;

    @Min(value = 1, message = "Maximum replicas must be at least 1")
    @Max(value = 100, message = "Maximum replicas cannot exceed 100")
    private Integer maxReplicas;

    @Min(value = 10, message = "Target CPU percent must be at least 10")
    @Max(value = 100, message = "Target CPU percent cannot exceed 100")
    private Integer targetCpuPercent;
}
