package ir.bita.esb.ws;

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

        Files.deleteIfExists(clientCert);
        Files.deleteIfExists(bitaCert);

        Path bitaTruststoreProps = baseDir.resolve("bita-truststore.properties");
        writeProperties(bitaKeystoreProps, "bita.jks", BITA_ALIAS);
        writeProperties(clientTruststoreProps, "client-truststore.jks", CLIENT_ALIAS);
        writeProperties(bitaTruststoreProps, "bita-truststore.jks", BITA_ALIAS);

        return new TestKeyStores(
                baseDir,
                clientJks,
                bitaJks,
                clientTruststoreJks,
                bitaTruststoreJks,
                bitaKeystoreProps.toAbsolutePath().toString(),
                clientTruststoreProps.toAbsolutePath().toString(),
                bitaTruststoreProps.toAbsolutePath().toString());
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
                org.apache.ws.security.crypto.provider=org.apache.ws.security.components.crypto.Merlin
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
            String clientTruststorePropsPath,
            String bitaTruststorePropsPath) {
    }
}
