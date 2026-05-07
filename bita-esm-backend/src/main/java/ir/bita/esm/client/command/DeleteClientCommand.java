package ir.bita.esm.client.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for soft deleting a client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteClientCommand {

    @NotNull(message = "Client ID is required")
    private Long clientId;
}
