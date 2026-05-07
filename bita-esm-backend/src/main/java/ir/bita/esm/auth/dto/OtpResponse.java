package ir.bita.esm.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for OTP request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponse {

    private String message;
    private int expiresInSeconds;
    private boolean isNewUser;

    /**
     * OTP code - only included in dev/test environments.
     */
    private String otpCode;
}
