package ir.bita.esm.route.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for updating a route.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRouteCommand {

    @NotNull(message = "Route ID is required")
    private Long routeId;

    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private Boolean active;
}
