package ir.bita.esm.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for creating a service collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceCollectionCommand {

    @NotBlank(message = "Collection name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Base path is required")
    @Size(max = 100, message = "Base path cannot exceed 100 characters")
    @Pattern(regexp = "^/esb/[a-z][a-z0-9-]*$", message = "Base path must start with /esb/ and contain only lowercase letters, numbers, and hyphens")
    private String basePath;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}
