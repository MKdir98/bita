package ir.bita.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility functions for JWT and token handling.
 */
public final class TokenUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private TokenUtils() {
        // Utility class
    }

    /**
     * Generate a simple JWT token (for testing/development).
     * In production, use a proper JWT library like jjwt.
     */
    public static String generateSimpleJwt(Map<String, Object> claims, String secret, long expirationSeconds) {
        try {
            // Header
            Map<String, String> header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String headerJson = objectMapper.writeValueAsString(header);
            String headerBase64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

            // Payload
            Map<String, Object> payload = new HashMap<>(claims);
            payload.put("iat", Instant.now().getEpochSecond());
            payload.put("exp", Instant.now().plusSeconds(expirationSeconds).getEpochSecond());
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            // Signature
            String toSign = headerBase64 + "." + payloadBase64;
            String signature = HashUtils.hmacSha256(toSign, secret);
            String signatureBase64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Base64.getDecoder().decode(signature));

            return headerBase64 + "." + payloadBase64 + "." + signatureBase64;
        } catch (Exception e) {
            throw new RuntimeException("JWT generation failed", e);
        }
    }

    /**
     * Decode JWT payload (without verification).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> decodeJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }

            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8);
            
            return objectMapper.readValue(payloadJson, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("JWT decode failed", e);
        }
    }

    /**
     * Verify simple JWT token.
     */
    public static boolean verifySimpleJwt(String token, String secret) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            // Verify signature
            String toSign = parts[0] + "." + parts[1];
            String expectedSignature = HashUtils.hmacSha256(toSign, secret);
            String expectedSignatureBase64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Base64.getDecoder().decode(expectedSignature));

            if (!parts[2].equals(expectedSignatureBase64)) {
                return false;
            }

            // Check expiration
            Map<String, Object> payload = decodeJwtPayload(token);
            if (payload.containsKey("exp")) {
                long exp = ((Number) payload.get("exp")).longValue();
                if (Instant.now().getEpochSecond() > exp) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract claim from JWT.
     */
    public static Object getJwtClaim(String token, String claimName) {
        Map<String, Object> payload = decodeJwtPayload(token);
        return payload.get(claimName);
    }

    /**
     * Check if JWT is expired.
     */
    public static boolean isJwtExpired(String token) {
        try {
            Map<String, Object> payload = decodeJwtPayload(token);
            if (payload.containsKey("exp")) {
                long exp = ((Number) payload.get("exp")).longValue();
                return Instant.now().getEpochSecond() > exp;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Get JWT expiration time.
     */
    public static Instant getJwtExpiration(String token) {
        Map<String, Object> payload = decodeJwtPayload(token);
        if (payload.containsKey("exp")) {
            long exp = ((Number) payload.get("exp")).longValue();
            return Instant.ofEpochSecond(exp);
        }
        return null;
    }

    /**
     * Generate a secure OTP code.
     */
    public static String generateOtp(int length) {
        StringBuilder otp = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < length; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    /**
     * Generate a 6-digit OTP.
     */
    public static String generateOtp6() {
        return generateOtp(6);
    }

    /**
     * Generate a refresh token.
     */
    public static String generateRefreshToken() {
        return CryptoUtils.generateSecureRandomString(32);
    }

    /**
     * Generate an API key.
     */
    public static String generateApiKey() {
        return CryptoUtils.generateApiKey();
    }

    /**
     * Mask a token for logging (show first and last 4 characters).
     */
    public static String maskToken(String token) {
        if (token == null || token.length() < 12) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    /**
     * Mask sensitive data for logging.
     */
    public static String maskSensitive(String data, int visibleChars) {
        if (data == null || data.length() <= visibleChars * 2) {
            return "****";
        }
        return data.substring(0, visibleChars) + "****" + data.substring(data.length() - visibleChars);
    }
}
