package ir.bita.common.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographic utility functions for encryption/decryption.
 */
public final class CryptoUtils {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {
        // Utility class
    }

    /**
     * Generate a new AES-256 secret key.
     */
    public static SecretKey generateAesKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGORITHM);
        keyGen.init(AES_KEY_SIZE, SECURE_RANDOM);
        return keyGen.generateKey();
    }

    /**
     * Generate a new AES key and return as Base64 string.
     */
    public static String generateAesKeyBase64() throws Exception {
        SecretKey key = generateAesKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Create a SecretKey from Base64 encoded string.
     */
    public static SecretKey keyFromBase64(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, AES_ALGORITHM);
    }

    /**
     * Encrypt plaintext using AES-GCM.
     * Returns Base64 encoded string containing IV + ciphertext.
     */
    public static String encrypt(String plaintext, SecretKey key) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Combine IV and ciphertext
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Encrypt plaintext using AES-GCM with Base64 key.
     */
    public static String encrypt(String plaintext, String base64Key) throws Exception {
        return encrypt(plaintext, keyFromBase64(base64Key));
    }

    /**
     * Decrypt ciphertext using AES-GCM.
     */
    public static String decrypt(String encrypted, SecretKey key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encrypted);

        // Extract IV and ciphertext
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * Decrypt ciphertext using AES-GCM with Base64 key.
     */
    public static String decrypt(String encrypted, String base64Key) throws Exception {
        return decrypt(encrypted, keyFromBase64(base64Key));
    }

    /**
     * Generate a secure random string (for API keys, tokens, etc.).
     */
    public static String generateSecureRandomString(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generate a 32-character API key.
     */
    public static String generateApiKey() {
        return generateSecureRandomString(24); // 24 bytes = 32 chars in Base64
    }

    /**
     * Generate secure random bytes.
     */
    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }
}
