package ir.bita.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashing utility functions.
 */
public final class HashUtils {

    private static final String SHA256 = "SHA-256";
    private static final String SHA512 = "SHA-512";
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private HashUtils() {
        // Utility class
    }

    /**
     * Calculate SHA-256 hash of a string.
     */
    public static String sha256(String input) {
        return hash(input, SHA256);
    }

    /**
     * Calculate SHA-256 hash and return as hex string.
     */
    public static String sha256Hex(String input) {
        return hashHex(input, SHA256);
    }

    /**
     * Calculate SHA-512 hash of a string.
     */
    public static String sha512(String input) {
        return hash(input, SHA512);
    }

    /**
     * Calculate SHA-512 hash and return as hex string.
     */
    public static String sha512Hex(String input) {
        return hashHex(input, SHA512);
    }

    /**
     * Calculate hash with salt (for password storage).
     * Returns Base64 encoded "salt:hash".
     */
    public static String hashWithSalt(String input) {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        
        byte[] hash = hashWithSaltBytes(input, salt);
        
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hashBase64 = Base64.getEncoder().encodeToString(hash);
        
        return saltBase64 + ":" + hashBase64;
    }

    /**
     * Verify a salted hash.
     */
    public static boolean verifyWithSalt(String input, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            if (parts.length != 2) {
                return false;
            }
            
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
            
            byte[] actualHash = hashWithSaltBytes(input, salt);
            
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Calculate HMAC-SHA256.
     */
    public static String hmacSha256(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    /**
     * Calculate HMAC-SHA256 and return as hex string.
     */
    public static String hmacSha256Hex(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    private static String hash(String input, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash calculation failed", e);
        }
    }

    private static String hashHex(String input, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash calculation failed", e);
        }
    }

    private static byte[] hashWithSaltBytes(String input, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256);
            digest.update(salt);
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Hash with salt failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Calculate checksum for data integrity.
     */
    public static String checksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256);
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Checksum calculation failed", e);
        }
    }
}
