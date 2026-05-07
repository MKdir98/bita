package ir.bita.esm.auth.repository;

import ir.bita.esm.auth.entity.UserOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for UserOtp entity.
 */
@Repository
public interface UserOtpRepository extends JpaRepository<UserOtp, Long> {

    /**
     * Finds the latest valid OTP for a mobile number and type.
     */
    Optional<UserOtp> findFirstByMobileNumberAndOtpTypeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String mobileNumber, 
            UserOtp.OtpType otpType,
            LocalDateTime now
    );

    /**
     * Finds OTP by mobile number and code.
     */
    Optional<UserOtp> findByMobileNumberAndOtpCodeAndUsedFalse(String mobileNumber, String otpCode);

    /**
     * Counts recent OTPs sent to a mobile number (for rate limiting).
     */
    @Query("SELECT COUNT(o) FROM UserOtp o WHERE o.mobileNumber = :mobile AND o.createdAt > :since")
    long countRecentOtps(@Param("mobile") String mobileNumber, @Param("since") LocalDateTime since);

    /**
     * Marks all OTPs for a mobile number as used.
     */
    @Modifying
    @Query("UPDATE UserOtp o SET o.used = true WHERE o.mobileNumber = :mobile AND o.used = false")
    void markAllAsUsed(@Param("mobile") String mobileNumber);

    /**
     * Deletes expired OTPs (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM UserOtp o WHERE o.expiresAt < :now")
    void deleteExpiredOtps(@Param("now") LocalDateTime now);
}
