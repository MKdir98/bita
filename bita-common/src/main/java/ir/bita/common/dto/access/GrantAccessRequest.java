package ir.bita.common.dto.access;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for granting a client access to a service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrantAccessRequest {

    @NotNull(message = "Client ID is required")
    @Positive(message = "Client ID must be positive")
    private Long clientId;

    @NotNull(message = "Service ID is required")
    @Positive(message = "Service ID must be positive")
    private Long serviceId;

    /**
     * Custom rate limit for this client on this service (requests per minute).
     * Null means use the endpoint's default rate limit.
     */
    @Min(value = 1, message = "Rate limit must be at least 1")
    private Integer customRateLimit;

    /**
     * When this access becomes valid. Null means immediately.
     */
    private LocalDateTime validFrom;

    /**
     * When this access expires. Null means no expiration.
     */
    private LocalDateTime validUntil;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
