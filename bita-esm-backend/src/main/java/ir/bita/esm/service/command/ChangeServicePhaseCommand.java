package ir.bita.esm.service.command;

import ir.bita.common.domain.ServicePhase;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for changing service phase.
 * When changing to ACTIVE: triggers K8s deployment.
 * When changing from ACTIVE: triggers K8s undeployment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeServicePhaseCommand {

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    @NotNull(message = "New phase is required")
    private ServicePhase newPhase;
}
