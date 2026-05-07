package ir.bita.common.security;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

/**
 * Utility functions for certificate handling.
 */
public final class CertificateUtils {

    private static final String X509_TYPE = "X.509";
    private static final String PKCS12_TYPE = "PKCS12";
    private static final String JKS_TYPE = "JKS";

    private CertificateUtils() {
        // Utility class
    }

    /**
     * Load X.509 certificate from PEM string.
     */
    public static X509Certificate loadCertificateFromPem(String pemCertificate) throws Exception {
        // Remove PEM headers/footers
        String certContent = pemCertificate
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");

        byte[] certBytes = Base64.getDecoder().decode(certContent);
        
        CertificateFactory factory = CertificateFactory.getInstance(X509_TYPE);
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    /**
     * Load X.509 certificate from file.
     */
    public static X509Certificate loadCertificateFromFile(String filePath) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance(X509_TYPE);
        try (InputStream is = new FileInputStream(filePath)) {
            return (X509Certificate) factory.generateCertificate(is);
        }
    }

    /**
     * Load certificate from classpath resource.
     */
    public static X509Certificate loadCertificateFromClasspath(String resourcePath) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance(X509_TYPE);
        try (InputStream is = CertificateUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            return (X509Certificate) factory.generateCertificate(is);
        }
    }

    /**
     * Load KeyStore from file.
     */
    public static KeyStore loadKeyStore(String filePath, String password, String type) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        try (InputStream is = new FileInputStream(filePath)) {
            keyStore.load(is, password.toCharArray());
        }
        return keyStore;
    }

    /**
     * Load PKCS12 KeyStore.
     */
    public static KeyStore loadPkcs12KeyStore(String filePath, String password) throws Exception {
        return loadKeyStore(filePath, password, PKCS12_TYPE);
    }

    /**
     * Load JKS KeyStore.
     */
    public static KeyStore loadJksKeyStore(String filePath, String password) throws Exception {
        return loadKeyStore(filePath, password, JKS_TYPE);
    }

    /**
     * Get private key from KeyStore.
     */
    public static PrivateKey getPrivateKey(KeyStore keyStore, String alias, String password) throws Exception {
        return (PrivateKey) keyStore.getKey(alias, password.toCharArray());
    }

    /**
     * Get certificate from KeyStore.
     */
    public static X509Certificate getCertificate(KeyStore keyStore, String alias) throws Exception {
        Certificate cert = keyStore.getCertificate(alias);
        if (cert instanceof X509Certificate) {
            return (X509Certificate) cert;
        }
        throw new RuntimeException("Certificate is not X509: " + alias);
    }

    /**
     * Check if certificate is valid (not expired).
     */
    public static boolean isValid(X509Certificate certificate) {
        try {
            certificate.checkValidity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if certificate is valid at a specific date.
     */
    public static boolean isValidAt(X509Certificate certificate, Date date) {
        try {
            certificate.checkValidity(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get certificate Subject DN.
     */
    public static String getSubjectDN(X509Certificate certificate) {
        return certificate.getSubjectX500Principal().getName();
    }

    /**
     * Get certificate Issuer DN.
     */
    public static String getIssuerDN(X509Certificate certificate) {
        return certificate.getIssuerX500Principal().getName();
    }

    /**
     * Get certificate serial number as hex string.
     */
    public static String getSerialNumberHex(X509Certificate certificate) {
        return certificate.getSerialNumber().toString(16);
    }

    /**
     * Get certificate expiration date.
     */
    public static Date getExpirationDate(X509Certificate certificate) {
        return certificate.getNotAfter();
    }

    /**
     * Get days until certificate expiration.
     */
    public static long getDaysUntilExpiration(X509Certificate certificate) {
        Date expiration = certificate.getNotAfter();
        long diff = expiration.getTime() - System.currentTimeMillis();
        return diff / (1000 * 60 * 60 * 24);
    }

    /**
     * Extract Common Name (CN) from certificate.
     */
    public static String getCommonName(X509Certificate certificate) {
        String dn = certificate.getSubjectX500Principal().getName();
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return null;
    }

    /**
     * Convert certificate to PEM format.
     */
    public static String toPem(X509Certificate certificate) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(certificate.getEncoded());
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN CERTIFICATE-----\n");
        
        // Add line breaks every 64 characters
        int index = 0;
        while (index < base64.length()) {
            pem.append(base64, index, Math.min(index + 64, base64.length()));
            pem.append("\n");
            index += 64;
        }
        
        pem.append("-----END CERTIFICATE-----");
        return pem.toString();
    }

    /**
     * Calculate certificate fingerprint (SHA-256).
     */
    public static String getFingerprint(X509Certificate certificate) throws Exception {
        byte[] encoded = certificate.getEncoded();
        return HashUtils.checksum(encoded);
    }
}
