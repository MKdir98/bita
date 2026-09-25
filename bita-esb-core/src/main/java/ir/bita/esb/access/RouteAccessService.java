package ir.bita.esb.access;

/**
 * Access policy facade injected into every Groovy route script via the GroovyShell Binding.
 *
 * <p>Route scripts reference it as {@code accessService} and use it for two purposes:
 * <ol>
 *   <li><b>WS-Security config</b> — retrieve the ESB's own keystore and the client trust store
 *       without the script author needing to know file-system paths.</li>
 *   <li><b>Access control</b> — check whether the calling client has been granted access to this
 *       route and whether it is within its rate limit.</li>
 * </ol>
 *
 * <p>Example usage inside a route script:
 * <pre>{@code
 * from("cxf:...")
 *     .process { exchange ->
 *         def clientId = accessService.getClientIdByApiKey(exchange.in.getHeader("X-API-Key"))
 *         if (!accessService.hasAccess(clientId)) throw new SecurityException("Access denied")
 *     }
 *     .to("cxf:...")
 * }</pre>
 */
public interface RouteAccessService {

    /**
     * Replaces the set of consumer certificates this service trusts, as synced from ESM (the
     * X.509 credentials of clients currently granted access). Implementations without
     * WS-Security trust stores may ignore it.
     */
    default void updateAuthorizedClientCertificates(java.util.List<String> certPems) throws Exception {
    }

    /** Path to the ESB gateway's own keystore properties file (BITA private key). */
    String getBitaKeyStore();

    /** Password for the BITA keystore. */
    String getBitaPassword();

    /**
     * Path to the trust store for verifying signatures from inbound clients.
     * Built from the X.509 credentials registered for clients that have access to this route.
     */
    String getClientTrustStore();

    /**
     * All client keys (X.509 certificate values) that are authorised to call this route.
     * Useful for building a dynamic trust store.
     */
    java.util.List<String> getClientKeys();

    /**
     * Returns true if the client has been granted access to this route and the grant is still valid.
     *
     * @param clientId the client ID extracted from the request
     */
    boolean hasAccess(String clientId);

    /**
     * Returns true if the client is within its rate limit for this route.
     * Always returns true when no rate limit is configured.
     */
    boolean checkRateLimit(String clientId);

    /** Resolve a client ID from an API key credential. */
    String getClientIdByApiKey(String apiKey);

    /** Resolve a client ID from a remote IP address. */
    String getClientIdByIp(String ip);

    /**
     * The CXF listen address for this gateway endpoint (e.g. {@code http://0.0.0.0:8080/ws}).
     * In production this reads from {@code HTTP_PORT}; in tests it returns the test-bound port.
     */
    String getGatewayAddress();

    /**
     * Path to the trust store used to verify the provider's response signatures.
     * Contains the provider's public certificate.
     */
    String getProviderTrustStore();

    /**
     * Alias of the provider's certificate inside the provider trust store.
     * Used as the encryption target when the gateway sends a request to the provider.
     */
    String getProviderAlias();
}
