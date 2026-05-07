package ir.bita.esm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for completing user registration with OTP verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterVerifyRequest {

    @NotBlank(message = "شماره موبایل الزامی است")
    @Pattern(regexp = "^09\\d{9}$", message = "شماره موبایل باید به فرمت 09xxxxxxxxx باشد")
    private String mobileNumber;

    @NotBlank(message = "کد تایید الزامی است")
    @Pattern(regexp = "^\\d{6}$", message = "کد تایید باید ۶ رقم باشد")
    private String otpCode;

    @NotBlank(message = "رمز عبور الزامی است")
    @Size(min = 6, message = "رمز عبور باید حداقل ۶ کاراکتر باشد")
    private String password;

    @Size(max = 100, message = "نام نباید بیش از ۱۰۰ کاراکتر باشد")
    private String fullName;
}
