package ir.bita.esb.access;

import ir.bita.esb.cache.AccessCache;
import ir.bita.esb.cache.ClientCache;
import ir.bita.esb.config.EsbConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Production implementation of {@link RouteAccessService} backed by the ESM Backend.
 *
 * <p>Constructed once per ESB pod startup (inside {@code SyncService.fullSync()}).
 * It builds JKS trust stores on disk from PEM-encoded certificates returned by the ESM
 * {@code GET /internal/v1/sync/services/{serviceId}/config} endpoint, then caches the
 * resulting file paths for the lifetime of the pod.
 *
 * <p>Access control (hasAccess / checkRateLimit / clientId resolution) is delegated to
 * the in-memory {@link AccessCache} and {@link ClientCache}, which are populated during
 * the same full-sync that constructs this object.
 *
 * <p>The test counterpart is the anonymous stub in {@code EchoWsSecurityIntegrationTest} —
 * that test never touches this class.
 */
@Slf4j
public class DefaultRouteAccessService implements RouteAccessService {

    private static final String TRUST_STORE_PASSWORD = "changeit";

    private final EsbConfig config;
    private final AccessCache accessCache;
    private final ClientCache clientCache;

    private volatile String providerAlias;
    private volatile String providerTrustStorePath;
    private volatile String clientTrustStorePath;
    private volatile List<String> clientCertPems;

    /** Startup constructor — no certs yet. Call {@link #rebuildTrustStores} after fullSync. */
    public DefaultRouteAccessService(EsbConfig config, AccessCache accessCache, ClientCache clientCache) {
        this.config = config;
        this.accessCache = accessCache;
        this.clientCache = clientCache;
        this.providerAlias = "provider";
        this.clientCertPems = List.of();
        this.providerTrustStorePath = null;
        this.clientTrustStorePath = null;
    }

    /**
     * Full constructor used in tests / legacy callers.
     *
     * @param providerAlias   alias stored in the provider trust store
     * @param providerCertPem PEM-encoded provider X.509 cert (null for non-WS-Security routes)
     * @param clientCertPems  PEM-encoded X.509 certs for all authorised clients
     */
    public DefaultRouteAccessService(
            EsbConfig config,
            AccessCache accessCache,
            ClientCache clientCache,
            String providerAlias,
            String providerCertPem,
            List<String> clientCertPems) throws Exception {
        this(config, accessCache, clientCache);
        rebuildTrustStores(providerAlias, providerCertPem, clientCertPems);
    }

    /** Rebuild trust stores from newly synced cert PEMs. Safe to call on hot-reload. */
    public void rebuildTrustStores(String providerAlias, String providerCertPem,
                                   List<String> clientCertPems) throws Exception {
        this.providerAlias = providerAlias != null ? providerAlias : "provider";
        this.clientCertPems = clientCertPems != null ? clientCertPems : List.of();

        Path tempDir = Files.createTempDirectory("esb-ts-" + config.getServiceId() + "-");
        log.info("Building WS-Security trust stores in {}", tempDir);

        this.providerTrustStorePath = (providerCertPem != null && !providerCertPem.isBlank())
                ? buildTrustStore(tempDir, "provider-trust",
                        List.of(this.providerAlias), List.of(providerCertPem))
                : null;

        this.clientTrustStorePath = !this.clientCertPems.isEmpty()
                ? buildClientTrustStore(tempDir, this.clientCertPems)
                : null;
    }

    // ── RouteAccessService ────────────────────────────────────────────────────

    @Override
    public void updateAuthorizedClientCertificates(List<String> certPems) throws Exception {
        rebuildTrustStores(providerAlias, null, certPems);
    }

    @Override
    public String getBitaKeyStore() {
        return config.getBitaKeystorePath();
    }

    @Override
    public String getBitaPassword() {
        return config.getBitaPassword();
    }

    @Override
    public String getClientTrustStore() {
        return clientTrustStorePath;
    }

    @Override
    public List<String> getClientKeys() {
        return clientCertPems;
    }

    @Override
    public boolean hasAccess(String clientId) {
        return accessCache.hasAnyAccess(clientId);
    }

    @Override
    public boolean checkRateLimit(String clientId) {
        return accessCache.checkRateLimitAny(clientId);
    }

    @Override
    public String getClientIdByApiKey(String apiKey) {
        return clientCache.getClientIdByApiKey(apiKey);
    }

    @Override
    public String getClientIdByIp(String ip) {
        return clientCache.getClientIdByIp(ip);
    }

    @Override
    public String getGatewayAddress() {
        return "http://0.0.0.0:" + config.getHttpPort() + "/ws";
    }

    @Override
    public String getProviderTrustStore() {
        return providerTrustStorePath;
    }

    @Override
    public String getProviderAlias() {
        return providerAlias;
    }

    // ── trust-store builder ───────────────────────────────────────────────────

    private String buildClientTrustStore(Path dir, List<String> pems) throws Exception {
        List<String> aliases = new ArrayList<>();
        for (int i = 0; i < pems.size(); i++) {
            aliases.add("client-" + i);
        }
        return buildTrustStore(dir, "client-trust", aliases, pems);
    }

    /**
     * Creates a JKS trust store from PEM certs and writes a Merlin properties file pointing to it.
     * Returns the absolute path to the properties file (what {@link RouteAccessService} methods return).
     */
    private String buildTrustStore(Path dir, String name, List<String> aliases, List<String> certPems)
            throws Exception {

        Path jksPath = dir.resolve(name + ".jks");

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (int i = 0; i < certPems.size(); i++) {
            String pem = certPems.get(i);
            if (pem == null || pem.isBlank()) continue;
            byte[] der = decodePem(pem);
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
            String alias = i < aliases.size() ? aliases.get(i) : "cert-" + i;
            ks.setCertificateEntry(alias, cert);
            log.debug("Added cert '{}' to trust store '{}'", alias, name);
        }

        try (FileOutputStream fos = new FileOutputStream(jksPath.toFile())) {
            ks.store(fos, TRUST_STORE_PASSWORD.toCharArray());
        }

        // Merlin properties — same format as TestKeyStoreGenerator.writeProperties
        Path propsPath = dir.resolve(name + ".properties");
        String props = """
                org.apache.ws.security.crypto.provider=org.apache.wss4j.common.crypto.Merlin
                org.apache.ws.security.crypto.merlin.keystore.type=jks
                org.apache.ws.security.crypto.merlin.keystore.password=%s
                org.apache.ws.security.crypto.merlin.keystore.file=%s
                """.formatted(TRUST_STORE_PASSWORD, jksPath.toAbsolutePath());
        Files.writeString(propsPath, props, StandardCharsets.UTF_8);

        log.info("Trust store '{}' written: {} entries", name, ks.size());
        return propsPath.toAbsolutePath().toString();
    }

    private static byte[] decodePem(String pem) {
        String stripped = pem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }
}
