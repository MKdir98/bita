package ir.bita.esm.auth.service;

import ir.bita.esm.auth.dto.*;
import ir.bita.esm.auth.entity.Role;
import ir.bita.esm.auth.entity.User;
import ir.bita.esm.auth.entity.UserOtp;
import ir.bita.esm.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for user authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    /**
     * Requests an OTP for login or registration.
     * @deprecated Use {@link #login(LoginRequest)} for existing users 
     *             or {@link #requestRegistrationOtp(RegisterRequest)} for new users.
     */
    @Deprecated
    @Transactional
    public OtpResponse requestOtp(RequestOtpRequest request) {
        String mobileNumber = request.getMobileNumber();
        boolean isNewUser = !userRepository.existsByMobileNumber(mobileNumber);
        
        if (!isNewUser) {
            // For existing users, guide them to use password-based login
            throw new IllegalArgumentException("لطفا از روش ورود با رمز عبور استفاده کنید");
        }
        
        // For new users, proceed with registration OTP
        UserOtp otp = otpService.generateAndSendOtp(mobileNumber, UserOtp.OtpType.REGISTER);

        OtpResponse.OtpResponseBuilder responseBuilder = OtpResponse.builder()
                .message("OTP sent successfully")
                .expiresInSeconds(otpService.getExpirationSeconds())
                .isNewUser(true);

        // Include OTP code in response for dev/local environments
        if (isDevEnvironment()) {
            responseBuilder.otpCode(otp.getOtpCode());
        }

        return responseBuilder.build();
    }

    /**
     * Verifies OTP and returns JWT tokens.
     */
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String mobileNumber = request.getMobileNumber();
        String otpCode = request.getOtpCode();

        // Validate OTP
        Optional<UserOtp> otpOpt = otpService.validateOtp(mobileNumber, otpCode);
        if (otpOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        UserOtp otp = otpOpt.get();

        // Get or create user
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseGet(() -> createNewUser(mobileNumber, request.getFullName()));

        // Update full name if provided during registration
        if (otp.getOtpType() == UserOtp.OtpType.REGISTER && request.getFullName() != null) {
            user.setFullName(request.getFullName());
            user = userRepository.save(user);
        }

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        log.info("User {} logged in successfully", mobileNumber);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .mobileNumber(user.getMobileNumber())
                        .fullName(user.getFullName())
                        .roles(user.getRoles())
                        .build())
                .build();
    }

    /**
     * Refreshes access token using refresh token.
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Not a refresh token");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isActive() || user.isDeleted()) {
            throw new IllegalArgumentException("User is not active");
        }

        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // Return same refresh token
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .mobileNumber(user.getMobileNumber())
                        .fullName(user.getFullName())
                        .roles(user.getRoles())
                        .build())
                .build();
    }

    /**
     * Gets current user information.
     */
    @Transactional(readOnly = true)
    public AuthResponse.UserInfo getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .mobileNumber(user.getMobileNumber())
                .fullName(user.getFullName())
                .roles(user.getRoles())
                .build();
    }

    // ============ Password-based Authentication Methods ============

    /**
     * Authenticates user with mobile number and password.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String mobileNumber = request.getMobileNumber();
        
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("شماره موبایل یا رمز عبور اشتباه است"));

        if (!user.isActive() || user.isDeleted()) {
            throw new IllegalArgumentException("حساب کاربری غیرفعال است");
        }

        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException("لطفا ابتدا رمز عبور خود را تنظیم کنید");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("شماره موبایل یا رمز عبور اشتباه است");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        log.info("User {} logged in with password", mobileNumber);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .mobileNumber(user.getMobileNumber())
                        .fullName(user.getFullName())
                        .roles(user.getRoles())
                        .build())
                .build();
    }

    /**
     * Requests OTP for new user registration.
     */
    @Transactional
    public OtpResponse requestRegistrationOtp(RegisterRequest request) {
        String mobileNumber = request.getMobileNumber();

        if (userRepository.existsByMobileNumber(mobileNumber)) {
            throw new IllegalArgumentException("این شماره موبایل قبلا ثبت نام شده است");
        }

        UserOtp otp = otpService.generateAndSendOtp(mobileNumber, UserOtp.OtpType.REGISTER);

        OtpResponse.OtpResponseBuilder responseBuilder = OtpResponse.builder()
                .message("کد تایید ارسال شد")
                .expiresInSeconds(otpService.getExpirationSeconds())
                .isNewUser(true);

        if (isDevEnvironment()) {
            responseBuilder.otpCode(otp.getOtpCode());
        }

        return responseBuilder.build();
    }

    /**
     * Verifies OTP and completes registration with password.
     */
    @Transactional
    public AuthResponse verifyRegistration(RegisterVerifyRequest request) {
        String mobileNumber = request.getMobileNumber();
        String otpCode = request.getOtpCode();

        // Validate OTP
        Optional<UserOtp> otpOpt = otpService.validateOtp(mobileNumber, otpCode);
        if (otpOpt.isEmpty()) {
            throw new IllegalArgumentException("کد تایید نامعتبر یا منقضی شده است");
        }

        UserOtp otp = otpOpt.get();
        if (otp.getOtpType() != UserOtp.OtpType.REGISTER) {
            throw new IllegalArgumentException("کد تایید نامعتبر است");
        }

        // Check user doesn't already exist (race condition protection)
        if (userRepository.existsByMobileNumber(mobileNumber)) {
            throw new IllegalArgumentException("این شماره موبایل قبلا ثبت نام شده است");
        }

        // Create user with password
        User user = User.builder()
                .mobileNumber(mobileNumber)
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        user.addRole(Role.VIEWER);
        user = userRepository.save(user);

        log.info("New user registered with mobile {}", mobileNumber);

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .mobileNumber(user.getMobileNumber())
                        .fullName(user.getFullName())
                        .roles(user.getRoles())
                        .build())
                .build();
    }

    /**
     * Requests OTP for password reset.
     */
    @Transactional
    public OtpResponse requestPasswordReset(ForgotPasswordRequest request) {
        String mobileNumber = request.getMobileNumber();

        if (!userRepository.existsByMobileNumber(mobileNumber)) {
            // Return success even if user doesn't exist (security: don't reveal user existence)
            return OtpResponse.builder()
                    .message("اگر این شماره در سیستم ثبت شده باشد، کد تایید ارسال خواهد شد")
                    .expiresInSeconds(otpService.getExpirationSeconds())
                    .isNewUser(false)
                    .build();
        }

        UserOtp otp = otpService.generateAndSendOtp(mobileNumber, UserOtp.OtpType.PASSWORD_RESET);

        OtpResponse.OtpResponseBuilder responseBuilder = OtpResponse.builder()
                .message("کد تایید ارسال شد")
                .expiresInSeconds(otpService.getExpirationSeconds())
                .isNewUser(false);

        if (isDevEnvironment()) {
            responseBuilder.otpCode(otp.getOtpCode());
        }

        return responseBuilder.build();
    }

    /**
     * Resets user password after OTP verification.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String mobileNumber = request.getMobileNumber();
        String otpCode = request.getOtpCode();

        // Validate OTP
        Optional<UserOtp> otpOpt = otpService.validateOtp(mobileNumber, otpCode);
        if (otpOpt.isEmpty()) {
            throw new IllegalArgumentException("کد تایید نامعتبر یا منقضی شده است");
        }

        UserOtp otp = otpOpt.get();
        if (otp.getOtpType() != UserOtp.OtpType.PASSWORD_RESET) {
            throw new IllegalArgumentException("کد تایید نامعتبر است");
        }

        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("کاربر یافت نشد"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password reset for user {}", mobileNumber);
    }

    private User createNewUser(String mobileNumber, String fullName) {
        User user = User.builder()
                .mobileNumber(mobileNumber)
                .fullName(fullName)
                .build();
        user.addRole(Role.VIEWER); // Default role
        user = userRepository.save(user);
        log.info("Created new user with mobile {}", mobileNumber);
        return user;
    }

    private boolean isDevEnvironment() {
        return "local".equals(activeProfile) || "dev".equals(activeProfile) || "test".equals(activeProfile);
    }
}
