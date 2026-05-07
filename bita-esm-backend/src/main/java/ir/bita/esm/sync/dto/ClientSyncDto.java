package ir.bita.esm.sync.dto;

import ir.bita.common.domain.CredentialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for client sync data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSyncDto {
    private Long id;
    private String name;
    private boolean active;
    private List<CredentialSyncDto> credentials;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CredentialSyncDto {
        private Long id;
        private CredentialType credentialType;
        private String credentialValue;
        private String secretHash;
        private boolean active;
        private LocalDateTime expiresAt;
    }
}
