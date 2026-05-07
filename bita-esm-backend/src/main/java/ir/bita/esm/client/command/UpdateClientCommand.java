package ir.bita.esm.client.command;

import ir.bita.common.dto.client.TagDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Command for updating an existing client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClientCommand {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @Size(max = 255, message = "Name cannot exceed 255 characters")
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    private String contactEmail;

    @Size(max = 11, message = "Phone number cannot exceed 11 characters")
    private String contactPhone;

    @Valid
    private List<TagDto> tags;

    private Boolean active;
}
