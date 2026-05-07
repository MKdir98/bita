package ir.bita.esm.service.command;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for creating a service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceCommand {

    @NotNull(message = "Collection ID is required")
    private Long collectionId;

    @NotBlank(message = "Service name is required")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "Version is required")
    @Size(max = 20, message = "Version cannot exceed 20 characters")
    @Pattern(regexp = "^\\d+\\.\\d+(\\.\\d+)?$", message = "Version must be in format x.y or x.y.z")
    private String version;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Min(value = 1, message = "Minimum replicas must be at least 1")
    @Max(value = 100, message = "Minimum replicas cannot exceed 100")
    @Builder.Default
    private Integer minReplicas = 2;

    @Min(value = 1, message = "Maximum replicas must be at least 1")
    @Max(value = 100, message = "Maximum replicas cannot exceed 100")
    @Builder.Default
    private Integer maxReplicas = 10;

    @Min(value = 10, message = "Target CPU percent must be at least 10")
    @Max(value = 100, message = "Target CPU percent cannot exceed 100")
    @Builder.Default
    private Integer targetCpuPercent = 80;
}
