package ir.bita.esm.client.command;

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
 * Command for adding a credential to a client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCredentialCommand {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotNull(message = "Credential type is required")
    private CredentialType credentialType;

    @NotBlank(message = "Credential value is required")
    @Size(max = 4096, message = "Credential value cannot exceed 4096 characters")
    private String credentialValue;

    /**
     * Secret value (for API keys, passwords, etc.)
     */
    private String secret;

    /**
     * Certificate content for X.509 credentials.
     */
    private String certificateContent;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private LocalDateTime expiresAt;
}
