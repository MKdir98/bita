package ir.bita.common.dto.access;

import ir.bita.common.domain.AccessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing a client's access to a service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAccessDto {

    private Long id;
    
    private Long clientId;
    
    private String clientName;
    
    private Long serviceId;
    
    private String serviceName;
    
    private AccessStatus status;
    
    /**
     * Custom rate limit for this client on this service.
     * Null means use the endpoint's default rate limit.
     */
    private Integer customRateLimit;
    
    /**
     * When this access becomes valid.
     */
    private LocalDateTime validFrom;
    
    /**
     * When this access expires. Null means no expiration.
     */
    private LocalDateTime validUntil;
    
    /**
     * Reason for granting access.
     */
    private String grantReason;
    
    /**
     * Reason for revocation (if revoked).
     */
    private String revokeReason;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private String grantedBy;
}
