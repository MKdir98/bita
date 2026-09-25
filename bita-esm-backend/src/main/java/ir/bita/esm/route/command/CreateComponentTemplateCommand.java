package ir.bita.esm.route.command;

import ir.bita.common.domain.ComponentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Command for creating a component template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateComponentTemplateCommand {

    @NotBlank(message = "Template name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotNull(message = "Component type is required")
    private ComponentType componentType;

    @NotBlank(message = "Class name is required")
    private String className;

    private Map<String, Object> configSchema;

    @Size(max = 50, message = "Category cannot exceed 50 characters")
    private String category;

    private String groovyCode;
}
