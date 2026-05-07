package ir.bita.esm.route.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for soft deleting a route.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteRouteCommand {

    @NotNull(message = "Route ID is required")
    private Long routeId;
}
