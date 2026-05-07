package ir.bita.esm.auth.service;

/**
 * Interface for SMS sending service.
 */
public interface SmsService {

    /**
     * Sends an OTP SMS to the given mobile number.
     *
     * @param mobileNumber the recipient mobile number
     * @param otpCode the OTP code to send
     * @return true if SMS was sent successfully
     */
    boolean sendOtp(String mobileNumber, String otpCode);
}
