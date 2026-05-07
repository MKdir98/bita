package ir.bita.esm.service.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for updating service scaling configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateServiceScalingCommand {

    @NotNull(message = "Service ID is required")
    private Long serviceId;

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
