package ir.bita.system.support.ws;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Generates test keystores and truststores for WS-Security integration tests using keytool.
 * Creates: client keystore, Bita keystore, and truststore containing client cert (for Bita).
 */
public final class TestKeyStoreGenerator {

    public static final String PASSWORD = "changeit";
    public static final String CLIENT_ALIAS = "client";
    public static final String BITA_ALIAS = "bita";
    public static final String PROVIDER_ALIAS = "provider";

    private TestKeyStoreGenerator() {
    }

    /**
     * Generate all keystores in the given directory.
     *
     * @return TestKeyStores with paths to generated files
     */
    public static TestKeyStores generate(Path baseDir) throws IOException, InterruptedException {
        Files.createDirectories(baseDir);

        Path clientJks = baseDir.resolve("client.jks");
        Path bitaJks = baseDir.resolve("bita.jks");
        Path clientTruststoreJks = baseDir.resolve("client-truststore.jks");
        Path bitaKeystoreProps = baseDir.resolve("bita-keystore.properties");
        Path clientKeystoreProps = baseDir.resolve("client-keystore.properties");
        Path clientTruststoreProps = baseDir.resolve("client-truststore.properties");

        runKeytool("-genkeypair",
                "-alias", CLIENT_ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-keystore", clientJks.toString(),
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=TestClient, OU=Test, O=Test, L=Test, ST=Test, C=US",
                "-validity", "365");

        runKeytool("-genkeypair",
                "-alias", BITA_ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-keystore", bitaJks.toString(),
                "-storepass", PASSWORD,
                "-keypass", PASSWORD,
                "-dname", "CN=BitaESB, OU=Test, O=Test, L=Test, ST=Test, C=US",
                "-validity", "365");

        Path clientCert = baseDir.resolve("client.cer");
        runKeytool("-exportcert",
                "-alias", CLIENT_ALIAS,
                "-keystore", clientJks.toString(),
                "-storepass", PASSWORD,
                "-file", clientCert.toString());

        runKeytool("-importcert",
                "-alias", CLIENT_ALIAS,
                "-keystore", clientTruststoreJks.toString(),
                "-storepass", PASSWORD,
                "-file", clientCert.toString(),
                "-noprompt");

        Path bitaTruststoreJks = baseDir.resolve("bita-truststore.jks");
        Path bitaCert = baseDir.resolve("bita.cer");
        runKeytool("-exportcert",
                "-alias", BITA_ALIAS,
                "-keystore", bitaJks.toString(),
                "-storepass", PASSWORD,
                "-file", bitaCert.toString());

        runKeytool("-importcert",
                "-alias", BITA_ALIAS,
                "-keystore", bitaTruststoreJks.toString(),
                "-storepass", PASSWORD,
                "-file", bitaCert.toString(),
                "-noprompt");

        Path clientPem = baseDir.resolve("client.pem");
        runKeytool("-exportcert", "-rfc", "-alias", CLIENT_ALIAS, "-keystore", clientJks.toString(),
                "-storepass", PASSWORD, "-file", clientPem.toString());

        Files.deleteIfExists(clientCert);
        Files.deleteIfExists(bitaCert);

        Path bitaTruststoreProps = baseDir.resolve("bita-truststore.properties");
        writeProperties(bitaKeystoreProps, "bita.jks", BITA_ALIAS);
        writeProperties(clientKeystoreProps, "client.jks", CLIENT_ALIAS);
        writeProperties(clientTruststoreProps, "client-truststore.jks", CLIENT_ALIAS);
        writeProperties(bitaTruststoreProps, "bita-truststore.jks", BITA_ALIAS);

        return new TestKeyStores(
                baseDir,
                clientJks,
                bitaJks,
                clientTruststoreJks,
                bitaTruststoreJks,
                bitaKeystoreProps.toAbsolutePath().toString(),
                clientKeystoreProps.toAbsolutePath().toString(),
                clientTruststoreProps.toAbsolutePath().toString(),
                bitaTruststoreProps.toAbsolutePath().toString());
    }

    /**
     * Generate three independent key pairs (client org, gateway/BITA, provider org) plus all
     * cross-truststores needed for a three-party WS-Security integration test.
     *
     * <pre>
     *  client  --[signs with client.key, encrypts with bita.cer]-->  gateway
     *  gateway --[signs with bita.key,   encrypts with provider.cer]--> provider
     *  provider response encrypted with USE_REQ_SIG_CERT (bita.cer back to gateway)
     *  gateway  response encrypted with USE_REQ_SIG_CERT (client.cer back to client)
     * </pre>
     */
    public static ThreePartyKeyStores generateThreeParty(Path baseDir) throws IOException, InterruptedException {
        Files.createDirectories(baseDir);

        Path clientJks   = baseDir.resolve("client.jks");
        Path bitaJks     = baseDir.resolve("bita.jks");
        Path providerJks = baseDir.resolve("provider.jks");

        runKeytool("-genkeypair", "-alias", CLIENT_ALIAS,   "-keyalg", "RSA", "-keysize", "2048",
                "-keystore", clientJks.toString(),   "-storepass", PASSWORD, "-keypass", PASSWORD,
                "-dname", "CN=ClientOrg, OU=Test, O=Test, L=Test, ST=Test, C=US", "-validity", "365");
        runKeytool("-genkeypair", "-alias", BITA_ALIAS,     "-keyalg", "RSA", "-keysize", "2048",
                "-keystore", bitaJks.toString(),     "-storepass", PASSWORD, "-keypass", PASSWORD,
                "-dname", "CN=BitaESB, OU=Test, O=Test, L=Test, ST=Test, C=US",  "-validity", "365");
        runKeytool("-genkeypair", "-alias", PROVIDER_ALIAS, "-keyalg", "RSA", "-keysize", "2048",
                "-keystore", providerJks.toString(), "-storepass", PASSWORD, "-keypass", PASSWORD,
                "-dname", "CN=ProviderOrg, OU=Test, O=Test, L=Test, ST=Test, C=US", "-validity", "365");

        Path clientCert   = baseDir.resolve("client.cer");
        Path bitaCert     = baseDir.resolve("bita.cer");
        Path providerCert = baseDir.resolve("provider.cer");

        runKeytool("-exportcert", "-alias", CLIENT_ALIAS,   "-keystore", clientJks.toString(),
                "-storepass", PASSWORD, "-file", clientCert.toString());
        runKeytool("-exportcert", "-alias", BITA_ALIAS,     "-keystore", bitaJks.toString(),
                "-storepass", PASSWORD, "-file", bitaCert.toString());
        runKeytool("-exportcert", "-alias", PROVIDER_ALIAS, "-keystore", providerJks.toString(),
                "-storepass", PASSWORD, "-file", providerCert.toString());

        // client trusts gateway (bita.cer)
        Path clientTrustJks = baseDir.resolve("client-trust.jks");
        runKeytool("-importcert", "-alias", BITA_ALIAS, "-keystore", clientTrustJks.toString(),
                "-storepass", PASSWORD, "-file", bitaCert.toString(), "-noprompt");

        // gateway inbound: trusts client (client.cer)
        Path gwInTrustJks = baseDir.resolve("gw-incoming-trust.jks");
        runKeytool("-importcert", "-alias", CLIENT_ALIAS, "-keystore", gwInTrustJks.toString(),
                "-storepass", PASSWORD, "-file", clientCert.toString(), "-noprompt");

        // gateway outbound: trusts provider (provider.cer)
        Path gwOutTrustJks = baseDir.resolve("gw-outgoing-trust.jks");
        runKeytool("-importcert", "-alias", PROVIDER_ALIAS, "-keystore", gwOutTrustJks.toString(),
                "-storepass", PASSWORD, "-file", providerCert.toString(), "-noprompt");

        // provider trusts gateway (bita.cer)
        Path providerTrustJks = baseDir.resolve("provider-trust.jks");
        runKeytool("-importcert", "-alias", BITA_ALIAS, "-keystore", providerTrustJks.toString(),
                "-storepass", PASSWORD, "-file", bitaCert.toString(), "-noprompt");

        Path clientPem = baseDir.resolve("client.pem");
        runKeytool("-exportcert", "-rfc", "-alias", CLIENT_ALIAS, "-keystore", clientJks.toString(),
                "-storepass", PASSWORD, "-file", clientPem.toString());

        Files.deleteIfExists(clientCert);
        Files.deleteIfExists(bitaCert);
        Files.deleteIfExists(providerCert);

        Path clientKsProps    = baseDir.resolve("client-keystore.properties");
        Path clientTrustProps = baseDir.resolve("client-trust.properties");
        Path gwKsProps        = baseDir.resolve("gw-keystore.properties");
        Path gwInTrustProps   = baseDir.resolve("gw-incoming-trust.properties");
        Path gwOutTrustProps  = baseDir.resolve("gw-outgoing-trust.properties");
        Path provKsProps      = baseDir.resolve("provider-keystore.properties");
        Path provTrustProps   = baseDir.resolve("provider-trust.properties");

        writeProperties(clientKsProps,    "client.jks",            CLIENT_ALIAS);
        writeProperties(clientTrustProps, "client-trust.jks",      BITA_ALIAS);
        writeProperties(gwKsProps,        "bita.jks",              BITA_ALIAS);
        writeProperties(gwInTrustProps,   "gw-incoming-trust.jks", CLIENT_ALIAS);
        writeProperties(gwOutTrustProps,  "gw-outgoing-trust.jks", PROVIDER_ALIAS);
        writeProperties(provKsProps,      "provider.jks",          PROVIDER_ALIAS);
        writeProperties(provTrustProps,   "provider-trust.jks",    BITA_ALIAS);

        return new ThreePartyKeyStores(
                baseDir,
                clientKsProps.toAbsolutePath().toString(),
                clientTrustProps.toAbsolutePath().toString(),
                gwKsProps.toAbsolutePath().toString(),
                gwInTrustProps.toAbsolutePath().toString(),
                gwOutTrustProps.toAbsolutePath().toString(),
                provKsProps.toAbsolutePath().toString(),
                provTrustProps.toAbsolutePath().toString());
    }

    /**
     * Three independent key stores for a three-party WS-Security test.
     *
     * @param clientKeystorePropsPath    client private key (signs outbound, decrypts inbound)
     * @param clientTrustPropsPath       bita.cer — client trusts the gateway's responses
     * @param gwKeystorePropsPath        bita private key — gateway signs in both directions
     * @param gwIncomingTrustPropsPath   client.cer — gateway trusts the client's request signatures
     * @param gwOutgoingTrustPropsPath   provider.cer — gateway encrypts to / verifies provider responses
     * @param providerKeystorePropsPath  provider private key (signs responses, decrypts gateway requests)
     * @param providerTrustPropsPath     bita.cer — provider trusts the gateway's request signatures
     */
    public record ThreePartyKeyStores(
            Path baseDir,
            String clientKeystorePropsPath,
            String clientTrustPropsPath,
            String gwKeystorePropsPath,
            String gwIncomingTrustPropsPath,
            String gwOutgoingTrustPropsPath,
            String providerKeystorePropsPath,
            String providerTrustPropsPath) {
    }

    private static void runKeytool(String... args) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        Path keytool = Path.of(javaHome, "bin", "keytool");
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add(keytool.toString());
        pb.command().addAll(java.util.List.of(args));
        pb.inheritIO();
        Process p = pb.start();
        if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new RuntimeException("keytool failed with exit " + p.exitValue());
        }
    }

    private static void writeProperties(Path file, String keystoreFile, String alias) throws IOException {
        String parent = file.getParent().toString().replace("\\", "/");
        String content = """
                org.apache.ws.security.crypto.provider=org.apache.wss4j.common.crypto.Merlin
                org.apache.ws.security.crypto.merlin.keystore.type=jks
                org.apache.ws.security.crypto.merlin.keystore.password=%s
                org.apache.ws.security.crypto.merlin.keystore.private.password=%s
                org.apache.ws.security.crypto.merlin.keystore.file=%s/%s
                org.apache.ws.security.crypto.merlin.keystore.alias=%s
                """.formatted(PASSWORD, PASSWORD, parent, keystoreFile, alias);
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    public record TestKeyStores(
            Path baseDir,
            Path clientJks,
            Path bitaJks,
            Path clientTruststoreJks,
            Path bitaTruststoreJks,
            String bitaKeystorePropsPath,
            String clientKeystorePropsPath,
            String clientTruststorePropsPath,
            String bitaTruststorePropsPath) {
    }
}
