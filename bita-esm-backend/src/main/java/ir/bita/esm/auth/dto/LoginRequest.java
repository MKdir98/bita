package ir.bita.esm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for password-based login.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "شماره موبایل الزامی است")
    @Pattern(regexp = "^09\\d{9}$", message = "شماره موبایل باید به فرمت 09xxxxxxxxx باشد")
    private String mobileNumber;

    @NotBlank(message = "رمز عبور الزامی است")
    @Size(min = 6, message = "رمز عبور باید حداقل ۶ کاراکتر باشد")
    private String password;
}
