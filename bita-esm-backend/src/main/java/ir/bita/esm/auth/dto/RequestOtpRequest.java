package ir.bita.esm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for requesting an OTP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestOtpRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^09\\d{9}$", message = "Mobile number must be in Iranian format (09xxxxxxxxx)")
    private String mobileNumber;
}
