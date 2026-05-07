package ir.bita.esm.client.dto;

import ir.bita.common.domain.CredentialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for Credential.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialResponse {

    private Long id;
    private CredentialType credentialType;
    private String credentialValue;
    private String description;
    private boolean active;
    private LocalDateTime expiresAt;
    private boolean expired;
    private LocalDateTime createdAt;
}
