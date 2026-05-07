package ir.bita.esm.auth.dto;

import ir.bita.esm.auth.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request DTO for creating a new user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^09\\d{9}$", message = "Mobile number must be in format 09XXXXXXXXX")
    private String mobileNumber;

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    private Set<Role> roles;
}
