package ir.bita.esm.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.auth.dto.*;
import ir.bita.esm.auth.security.CurrentUser;
import ir.bita.esm.auth.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;

    // ============ Password-based Authentication Endpoints ============

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Login with mobile number and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authenticationService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register/request-otp")
    @Operation(summary = "Request Registration OTP", description = "Request an OTP for new user registration")
    public ResponseEntity<OtpResponse> requestRegistrationOtp(@Valid @RequestBody RegisterRequest request) {
        OtpResponse response = authenticationService.requestRegistrationOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register/verify")
    @Operation(summary = "Verify Registration", description = "Verify OTP and complete registration with password")
    public ResponseEntity<AuthResponse> verifyRegistration(@Valid @RequestBody RegisterVerifyRequest request) {
        AuthResponse response = authenticationService.verifyRegistration(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot Password", description = "Request an OTP for password reset")
    public ResponseEntity<OtpResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        OtpResponse response = authenticationService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset Password", description = "Reset password using OTP verification")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    // ============ Legacy OTP-based Authentication Endpoints (Deprecated) ============

    @Deprecated
    @PostMapping("/request-otp")
    @Operation(summary = "Request OTP (Deprecated)", description = "Request an OTP for login or registration - Use /login or /register/request-otp instead")
    public ResponseEntity<OtpResponse> requestOtp(@Valid @RequestBody RequestOtpRequest request) {
        OtpResponse response = authenticationService.requestOtp(request);
        return ResponseEntity.ok(response);
    }

    @Deprecated
    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP (Deprecated)", description = "Verify OTP and get JWT tokens - Use /login or /register/verify instead")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authenticationService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token", description = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authenticationService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Logout (client should discard tokens)")
    public ResponseEntity<Void> logout() {
        // JWT tokens are stateless, client should discard the token
        // For refresh token invalidation, we would need a token blacklist (Redis)
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get Current User", description = "Get information about the currently authenticated user")
    public ResponseEntity<AuthResponse.UserInfo> getCurrentUser(@CurrentUser Long userId) {
        AuthResponse.UserInfo userInfo = authenticationService.getCurrentUser(userId);
        return ResponseEntity.ok(userInfo);
    }
}
