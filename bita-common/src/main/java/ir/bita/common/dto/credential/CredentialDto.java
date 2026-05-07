package ir.bita.common.dto.credential;

import ir.bita.common.domain.CredentialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing a client credential.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialDto {

    private Long id;
    
    private Long clientId;
    
    private CredentialType type;
    
    /**
     * The credential value. Content depends on type:
     * - IP_ADDRESS: IP address or CIDR (e.g., "192.168.1.100" or "192.168.1.0/24")
     * - X509_CERTIFICATE: Certificate subject DN or thumbprint
     * - API_KEY: The API key (masked in responses)
     * - OAUTH2: OAuth client ID
     * - BASIC_AUTH: Username
     */
    private String value;
    
    /**
     * Optional description for the credential.
     */
    private String description;
    
    /**
     * When the credential expires (null means no expiration).
     */
    private LocalDateTime expiresAt;
    
    private boolean active;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
