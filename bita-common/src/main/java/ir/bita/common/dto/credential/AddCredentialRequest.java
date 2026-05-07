package ir.bita.common.dto.credential;

import ir.bita.common.domain.CredentialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for adding a new credential to a client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCredentialRequest {

    @NotNull(message = "Credential type is required")
    private CredentialType type;

    @NotBlank(message = "Credential value is required")
    @Size(max = 4096, message = "Credential value cannot exceed 4096 characters")
    private String value;

    /**
     * Secret value for certain credential types (e.g., password for BASIC_AUTH, secret for API_KEY).
     * This value is write-only and never returned in responses.
     */
    @Size(max = 1024, message = "Secret cannot exceed 1024 characters")
    private String secret;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    /**
     * When the credential should expire. Null means no expiration.
     */
    private LocalDateTime expiresAt;

    /**
     * For X509_CERTIFICATE: The certificate content in PEM format.
     */
    private String certificateContent;
}
