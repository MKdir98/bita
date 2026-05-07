package ir.bita.esm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for verifying an OTP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^09\\d{9}$", message = "Mobile number must be in Iranian format (09xxxxxxxxx)")
    private String mobileNumber;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP code must be 6 digits")
    private String otpCode;

    /**
     * Full name for registration (optional for login).
     */
    private String fullName;
}
