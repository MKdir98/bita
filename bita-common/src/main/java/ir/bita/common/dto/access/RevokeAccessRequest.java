package ir.bita.common.dto.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for revoking a client's access to a service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokeAccessRequest {

    @NotBlank(message = "Revocation reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
