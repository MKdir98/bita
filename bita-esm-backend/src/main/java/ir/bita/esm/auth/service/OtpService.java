package ir.bita.esm.auth.service;

import ir.bita.esm.auth.entity.UserOtp;
import ir.bita.esm.auth.repository.UserOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for OTP generation and validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final UserOtpRepository otpRepository;
    private final SmsService smsService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.length:6}")
    private int otpLength;

    @Value("${app.otp.expiration-minutes:5}")
    private int expirationMinutes;

    /**
     * Maximum OTPs allowed per mobile per hour (rate limiting).
     */
    private static final int MAX_OTPS_PER_HOUR = 5;

    /**
     * Generates and sends a new OTP.
     *
     * @param mobileNumber the target mobile number
     * @param otpType the purpose of the OTP
     * @return the generated OTP entity
     * @throws IllegalStateException if rate limit is exceeded
     */
    @Transactional
    public UserOtp generateAndSendOtp(String mobileNumber, UserOtp.OtpType otpType) {
        // Check rate limit
        long recentOtps = otpRepository.countRecentOtps(mobileNumber, LocalDateTime.now().minusHours(1));
        if (recentOtps >= MAX_OTPS_PER_HOUR) {
            throw new IllegalStateException("Too many OTP requests. Please try again later.");
        }

        // Invalidate previous OTPs for this mobile
        otpRepository.markAllAsUsed(mobileNumber);

        // Generate new OTP
        String otpCode = generateOtpCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);

        UserOtp otp = UserOtp.builder()
                .mobileNumber(mobileNumber)
                .otpCode(otpCode)
                .otpType(otpType)
                .expiresAt(expiresAt)
                .build();

        otp = otpRepository.save(otp);

        // Send OTP via SMS
        smsService.sendOtp(mobileNumber, otpCode);

        log.info("Generated OTP for mobile {} type {}", mobileNumber, otpType);
        return otp;
    }

    /**
     * Validates an OTP.
     *
     * @param mobileNumber the mobile number
     * @param otpCode the OTP code to validate
     * @return the validated OTP if valid
     */
    @Transactional
    public Optional<UserOtp> validateOtp(String mobileNumber, String otpCode) {
        Optional<UserOtp> otpOpt = otpRepository.findByMobileNumberAndOtpCodeAndUsedFalse(mobileNumber, otpCode);
        
        if (otpOpt.isEmpty()) {
            log.warn("OTP validation failed: no matching OTP for mobile {}", mobileNumber);
            return Optional.empty();
        }

        UserOtp otp = otpOpt.get();
        
        if (otp.isExpired()) {
            log.warn("OTP validation failed: OTP expired for mobile {}", mobileNumber);
            return Optional.empty();
        }

        // Mark as used
        otp.markAsUsed();
        otpRepository.save(otp);

        log.info("OTP validated successfully for mobile {}", mobileNumber);
        return Optional.of(otp);
    }

    /**
     * Gets the expiration time in seconds.
     */
    public int getExpirationSeconds() {
        return expirationMinutes * 60;
    }

    /**
     * Generates a random numeric OTP code.
     */
    private String generateOtpCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }
}
