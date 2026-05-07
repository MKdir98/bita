package ir.bita.esm.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Mock SMS service for local development and testing.
 * Logs OTP instead of sending actual SMS.
 */
@Service
@Profile({"local", "dev", "test"})
@Slf4j
public class MockSmsService implements SmsService {

    @Override
    public boolean sendOtp(String mobileNumber, String otpCode) {
        log.info("========================================");
        log.info("MOCK SMS to {}: Your OTP code is {}", mobileNumber, otpCode);
        log.info("========================================");
        return true;
    }
}
