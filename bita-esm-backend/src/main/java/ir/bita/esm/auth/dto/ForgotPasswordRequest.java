package ir.bita.esm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for initiating password reset.
 * Sends OTP to the registered mobile number.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank(message = "شماره موبایل الزامی است")
    @Pattern(regexp = "^09\\d{9}$", message = "شماره موبایل باید به فرمت 09xxxxxxxxx باشد")
    private String mobileNumber;
}
