package ir.bita.esm.service.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Command for granting service access to a client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrantAccessCommand {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    /**
     * Custom rate limit for this client (requests per minute).
     * Null means use default.
     */
    private Integer customRateLimit;

    /**
     * When access becomes valid. Null means immediately.
     */
    private LocalDateTime validFrom;

    /**
     * When access expires. Null means no expiration.
     */
    private LocalDateTime validUntil;

    @Size(max = 500, message = "Grant reason cannot exceed 500 characters")
    private String grantReason;
}
