package ir.bita.esm.auth.dto;

import ir.bita.esm.auth.entity.Role;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request DTO for updating a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    private Boolean active;

    private Set<Role> roles;
}
