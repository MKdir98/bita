package ir.bita.esm.service.dto;

import ir.bita.common.domain.AccessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for ServiceAccess.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAccessResponse {

    private Long id;
    private Long clientId;
    private String clientName;
    private Long serviceId;
    private String serviceName;
    private String serviceVersion;
    private AccessStatus status;
    private Integer customRateLimit;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String grantReason;
    private String revokeReason;
    private boolean currentlyValid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
