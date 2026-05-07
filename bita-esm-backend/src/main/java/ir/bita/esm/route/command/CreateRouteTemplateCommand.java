package ir.bita.esm.route.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Command for creating a route template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRouteTemplateCommand {

    @NotBlank(message = "Template name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private Long fromEndpointTemplateId;

    private Long toEndpointTemplateId;

    private Map<String, Object> configSchema;

    private Map<String, Object> componentConfig;

    @Size(max = 50, message = "Category cannot exceed 50 characters")
    private String category;
}
