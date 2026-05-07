package ir.bita.common.dto.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a new client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClientRequest {

    @NotBlank(message = "Client name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Email(message = "Invalid email format")
    private String contactEmail;

    @Pattern(regexp = "^09\\d{9}$", message = "Phone must be in Iranian mobile format (09xxxxxxxxx)")
    private String contactPhone;

    @Valid
    private List<TagDto> tags;
}
