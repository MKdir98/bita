package ir.bita.esm.client.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for removing a credential from a client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoveCredentialCommand {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotNull(message = "Credential ID is required")
    private Long credentialId;
}
