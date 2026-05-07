package ir.bita.esm.client.entity;

import ir.bita.common.domain.CredentialType;
import ir.bita.esm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * Credential entity for client authentication.
 */
@Entity
@Table(name = "credential", indexes = {
        @Index(name = "idx_credential_client", columnList = "client_id"),
        @Index(name = "idx_credential_type", columnList = "credential_type"),
        @Index(name = "idx_credential_value", columnList = "credential_value"),
        @Index(name = "idx_credential_active", columnList = "is_active")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class Credential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 20)
    private CredentialType credentialType;

    /**
     * The credential value. Content depends on type:
     * - IP_ADDRESS: IP address or CIDR
     * - X509_CERTIFICATE: Certificate subject DN or thumbprint
     * - API_KEY: The API key
     * - OAUTH2: OAuth client ID
     * - BASIC_AUTH: Username
     */
    @Column(name = "credential_value", nullable = false, length = 4096)
    private String credentialValue;

    /**
     * Secret value (hashed for security).
     * Used for API_KEY secret, BASIC_AUTH password, etc.
     */
    @Column(name = "secret_hash", length = 512)
    private String secretHash;

    /**
     * For X509_CERTIFICATE: The certificate content in PEM format.
     */
    @Column(name = "certificate_content", columnDefinition = "TEXT")
    private String certificateContent;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Checks if credential is expired.
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Checks if credential is valid (active and not expired).
     */
    public boolean isValid() {
        return active && !isExpired();
    }

    /**
     * Deactivates this credential.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Activates this credential.
     */
    public void activate() {
        this.active = true;
    }
}
