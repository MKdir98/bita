package ir.bita.esm.route.command;

import ir.bita.common.domain.ComponentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Command for updating a component template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateComponentTemplateCommand {

    @NotNull(message = "Template ID is required")
    private Long id;

    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private ComponentType componentType;

    @Size(max = 500, message = "Component class cannot exceed 500 characters")
    private String className;

    private Map<String, Object> configSchema;

    private String groovyCode;
}
