package ir.bita.common.domain;

/**
 * Enum representing the types of credentials that clients can use for authentication.
 */
public enum CredentialType {

    /**
     * IP address or CIDR range for IP-based authentication.
     * Example: "192.168.1.100" or "192.168.1.0/24"
     */
    IP_ADDRESS("IP Address", "IP address or CIDR range"),

    /**
     * X.509 certificate for WS-Security 1.1 authentication.
     * Used for SOAP services requiring message signing and encryption.
     */
    X509_CERTIFICATE("X.509 Certificate", "X.509 certificate for WS-Security"),

    /**
     * API key for REST API authentication.
     * Typically passed in request headers.
     */
    API_KEY("API Key", "API key for REST authentication"),

    /**
     * OAuth 2.0 token-based authentication.
     * Supports various OAuth flows.
     */
    OAUTH2("OAuth 2.0", "OAuth 2.0 token authentication"),

    /**
     * Basic authentication with username and password.
     * Base64 encoded credentials in Authorization header.
     */
    BASIC_AUTH("Basic Auth", "Username and password authentication");

    private final String displayName;
    private final String description;

    CredentialType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if this credential type requires a certificate.
     */
    public boolean requiresCertificate() {
        return this == X509_CERTIFICATE;
    }

    /**
     * Checks if this credential type uses token-based authentication.
     */
    public boolean isTokenBased() {
        return this == API_KEY || this == OAUTH2;
    }
}
