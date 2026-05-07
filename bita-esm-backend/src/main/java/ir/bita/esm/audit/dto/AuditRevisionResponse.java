package ir.bita.esm.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRevisionResponse {
    private Long revisionId;
    private LocalDateTime revisionDate;
    private String revisionType;
    private Long userId;
    private String username;
    private String ipAddress;
}
