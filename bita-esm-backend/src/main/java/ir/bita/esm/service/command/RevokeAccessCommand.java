package ir.bita.esm.service.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for revoking service access from a client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokeAccessCommand {

    @NotNull(message = "Access ID is required")
    private Long accessId;

    @Size(max = 500, message = "Revoke reason cannot exceed 500 characters")
    private String reason;
}
