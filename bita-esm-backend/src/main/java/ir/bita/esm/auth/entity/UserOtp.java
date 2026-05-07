package ir.bita.esm.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity for storing OTP codes for user authentication.
 */
@Entity
@Table(name = "user_otp", indexes = {
        @Index(name = "idx_user_otp_mobile", columnList = "mobile_number"),
        @Index(name = "idx_user_otp_expires", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mobile_number", nullable = false, length = 11)
    private String mobileNumber;

    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_type", nullable = false, length = 20)
    private OtpType otpType;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * OTP purpose types.
     */
    public enum OtpType {
        REGISTER,        // For new user registration
        PASSWORD_RESET   // For password recovery
    }

    /**
     * Checks if OTP is expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Checks if OTP is valid (not expired and not used).
     */
    public boolean isValid() {
        return !isExpired() && !used;
    }

    /**
     * Marks OTP as used.
     */
    public void markAsUsed() {
        this.used = true;
    }
}
