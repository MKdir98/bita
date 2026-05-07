package ir.bita.common.dto.service;

import ir.bita.common.domain.ServicePhase;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for changing a service's phase.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeServicePhaseRequest {

    @NotNull(message = "Target phase is required")
    private ServicePhase targetPhase;

    /**
     * Optional reason for the phase change (for audit purposes).
     */
    private String reason;
}
