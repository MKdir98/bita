package ir.bita.esm.route.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Command for creating an endpoint template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEndpointTemplateCommand {

    @NotBlank(message = "Template name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotBlank(message = "Camel YAML is required")
    @Size(max = 10000, message = "Camel YAML cannot exceed 10000 characters")
    private String camelYaml;

    private List<Long> attachmentIds;

    private Map<String, Object> configSchema;

    private Integer defaultRateLimit;

    @Size(max = 50, message = "Category cannot exceed 50 characters")
    private String category;
}
