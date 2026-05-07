package ir.bita.esm.sync.dto;

import ir.bita.common.domain.AccessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for access rule sync data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessSyncDto {
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
    private boolean currentlyValid;
}
