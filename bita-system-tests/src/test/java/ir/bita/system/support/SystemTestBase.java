package ir.bita.system.support;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import ir.bita.common.domain.VariableType;
import ir.bita.esb.EsbCoreApplication;
import ir.bita.esb.config.EsbConfig;
import ir.bita.esm.EsmBackendApplication;
import ir.bita.common.domain.CredentialType;
import ir.bita.esm.client.command.AddCredentialCommand;
import ir.bita.esm.client.command.CreateClientCommand;
import ir.bita.esm.client.handler.AddCredentialHandler;
import ir.bita.esm.client.handler.CreateClientHandler;
import ir.bita.esm.llm.tool.ToolRegistry;
import ir.bita.esm.service.command.GrantAccessCommand;
import ir.bita.esm.service.handler.GrantAccessHandler;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.repository.GroovyTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * One real ESM (Spring Boot on a random port, H2 in PostgreSQL mode) per test class, and real
 * ESB instances started with {@link EsbCoreApplication#start} — the same wiring as the ESB's
 * {@code main()} — that fetch their Groovy script from that ESM over its internal sync API.
 * Nothing here builds a Camel route by hand.
 */
@SpringBootTest(classes = EsmBackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:system;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=ir.bita.system.support.TestFriendlyH2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:h2-compat.sql",
        "app.internal.api-key=" + SystemTestBase.INTERNAL_API_KEY,
        "app.llm.openai.enabled=false",
        "app.llm.boofai.enabled=false",
        // no broker in these tests: don't pre-create topics, and fail an event send fast
        // (DomainEventPublisher logs it; the business change itself still commits)
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.producer.properties.max.block.ms=200",
})
@org.springframework.context.annotation.Import(SystemTestBase.LocateTestEsbs.class)
public abstract class SystemTestBase {

    public static final String INTERNAL_API_KEY = "system-test-internal-key";

    /**
     * The sandbox ESB (ESB_MODE=sandbox in production): the same ESB code, running no service,
     * that trial-runs a proposed template before ESM registers it. One for the whole test JVM.
     */
    private static String sandboxUrl;

    static synchronized String sandboxEsb() {
        if (sandboxUrl == null) {
            try {
                int port = freePort();
                EsbCoreApplication.start(EsbConfig.builder()
                                .sandbox(true)
                                .httpPort(port)
                                .esmBaseUrl("")
                                .esmApiKey("")
                                .kafkaBootstrapServers("localhost:0")
                                .kafkaGroupId("system-test-sandbox")
                                .redisHost("localhost")
                                .elasticsearchUrl("")
                                .build())
                        .toCompletionStage().toCompletableFuture().get(60, TimeUnit.SECONDS);
                sandboxUrl = "http://127.0.0.1:" + port;
            } catch (Exception e) {
                throw new IllegalStateException("sandbox ESB did not start", e);
            }
        }
        return sandboxUrl;
    }

    @org.springframework.test.context.DynamicPropertySource
    static void sandbox(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("app.esb.sandbox-url", SystemTestBase::sandboxEsb);
    }

    @LocalServerPort
    protected int esmPort;

    @Autowired
    protected GroovyTemplateRepository templateRepository;
    @Autowired
    protected ToolRegistry toolRegistry;
    @Autowired
    protected CreateClientHandler createClientHandler;
    @Autowired
    protected AddCredentialHandler addCredentialHandler;
    @Autowired
    protected GrantAccessHandler grantAccessHandler;

    /**
     * Where each test-started ESB listens, so ESM's real {@code EsbReloadNotifier} can reach it
     * the way it reaches a service's Kubernetes Service in production.
     */
    public static final Map<Long, String> RUNNING_ESBS = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.boot.test.context.TestConfiguration
    public static class LocateTestEsbs {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        ir.bita.esm.route.service.EsbInstanceLocator testEsbLocator() {
            return serviceId -> java.util.Optional.ofNullable(RUNNING_ESBS.get(serviceId));
        }
    }

    private final List<EsbCoreApplication.Running> esbs = new ArrayList<>();
    private final List<Vertx> backends = new ArrayList<>();

    @AfterEach
    void stopEverything() throws Exception {
        for (EsbCoreApplication.Running esb : esbs) {
            RUNNING_ESBS.remove(esb.config().getServiceId());
            esb.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
        esbs.clear();
        for (Vertx v : backends) {
            v.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
        backends.clear();
    }

    /** Starts a real ESB for one service, pointed at this test's ESM. */
    protected EsbCoreApplication.Running startEsb(long serviceId) throws Exception {
        return startEsb(serviceId, "", "");
    }

    /** Same, with the gateway's own keystore (what production mounts as a K8s secret). */
    protected EsbCoreApplication.Running startEsb(long serviceId, String bitaKeystorePath, String bitaPassword)
            throws Exception {
        EsbConfig config = EsbConfig.builder()
                .serviceId(serviceId)
                .httpPort(freePort())
                .esmBaseUrl("http://localhost:" + esmPort)
                .esmApiKey(INTERNAL_API_KEY)
                .bitaKeystorePath(bitaKeystorePath)
                .bitaPassword(bitaPassword)
                .kafkaBootstrapServers("localhost:0")
                .kafkaGroupId("system-test-" + serviceId)
                .redisHost("localhost")
                .redisPort(0)
                .redisPassword("")
                .elasticsearchUrl("")
                .build();
        EsbCoreApplication.Running running = EsbCoreApplication.start(config)
                .toCompletionStage().toCompletableFuture().get(60, TimeUnit.SECONDS);
        esbs.add(running);
        RUNNING_ESBS.put(serviceId, "http://127.0.0.1:" + config.getHttpPort());
        return running;
    }

    /**
     * A provider backend on a free port: answers every request with a JSON body carrying
     * {@code marker}, the request path and the X-Client-Id the gateway forwarded, so a test can
     * tell which backend a call reached and on whose behalf.
     */
    protected Backend startBackend(String marker) throws Exception {
        return startBackend(marker, freePort());
    }

    protected Backend startBackend(String marker, int port) throws Exception {
        Vertx vertx = Vertx.vertx();
        backends.add(vertx);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server = vertx.createHttpServer()
                .requestHandler(req -> {
                    calls.incrementAndGet();
                    req.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("marker", marker).put("path", req.path())
                                    .put("clientId", req.getHeader("X-Client-Id"))
                                    .put("apiKeySeen", req.getHeader("X-API-Key")).encode());
                })
                .listen(port)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        return new Backend(vertx, server, port, marker, calls);
    }

    /** {@code calls}: how many requests reached this provider. */
    public record Backend(Vertx vertx, HttpServer server, int port, String marker,
                          java.util.concurrent.atomic.AtomicInteger calls) {
        public String address() {
            return "http://127.0.0.1:" + port + "/api";
        }
    }

    /** Stores a GroovyTemplate whose script is a real resource under {@code templates/}. */
    protected GroovyTemplate seedTemplate(String resourceName, String description,
                                          List<GroovyTemplate.VariableMetadata> variables) throws IOException {
        String script;
        try (InputStream in = getClass().getResourceAsStream("/templates/" + resourceName + ".groovy")) {
            if (in == null) {
                throw new IllegalStateException("missing template resource: " + resourceName);
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return templateRepository.save(GroovyTemplate.builder()
                .name(resourceName + "-" + UUID.randomUUID().toString().substring(0, 8))
                .description(description)
                .scriptText(script)
                .variables(new ArrayList<>(variables))
                .build());
    }

    protected GroovyTemplate seedRestProxyTemplate() throws IOException {
        return seedTemplate("rest-proxy", "REST-to-REST proxy over Vert.x", List.of(
                variable("pvAddress", "آدرس سرویس ارائه‌دهنده", VariableType.STRING),
                variable("gwPort", "پورت گیت‌وی", VariableType.PORT),
                variable("gwPath", "مسیر Ingress", VariableType.STRING)));
    }

    protected static GroovyTemplate.VariableMetadata variable(String name, String label, VariableType type) {
        return new GroovyTemplate.VariableMetadata(name, label, null, type, true, null, null);
    }

    /** Runs a production tool exactly as a confirmed tool call would. */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> executeTool(String name, Map<String, Object> args) {
        Map<String, Object> result = toolRegistry.executeTool(name, args);
        if (Boolean.TRUE.equals(result.get("error"))) {
            throw new AssertionError(name + " failed: " + result.get("message"));
        }
        return result;
    }

    /**
     * A consumer organisation registered in ESM with its X.509 certificate and granted access to
     * the service — through the same handlers the ESM API uses.
     */
    protected long grantConsumer(long serviceId, String name, String certificatePem) {
        var client = createClientHandler.handle(CreateClientCommand.builder()
                .name(name + "-" + UUID.randomUUID().toString().substring(0, 6)).build());
        addCredentialHandler.handle(AddCredentialCommand.builder()
                .clientId(client.getId())
                .credentialType(CredentialType.X509_CERTIFICATE)
                .credentialValue(certificatePem)
                .build());
        grantAccessHandler.handle(GrantAccessCommand.builder()
                .clientId(client.getId())
                .serviceId(serviceId)
                .grantReason("system test")
                .build());
        return client.getId();
    }

    /**
     * A consumer organisation registered in ESM with an API key and granted access to the
     * service; returns the key it calls with.
     */
    protected String grantApiKeyConsumer(long serviceId, String name) {
        return grantApiKeyConsumerWithKey(serviceId, name, "key-" + UUID.randomUUID()).apiKey();
    }

    /** A consumer with API-key credential, as ESM knows it: its client id and its access grant. */
    public record ApiKeyConsumer(long clientId, long accessId, String apiKey) {
    }

    protected ApiKeyConsumer grantApiKeyConsumerWithKey(long serviceId, String name, String key) {
        var client = createClientHandler.handle(CreateClientCommand.builder()
                .name(name + "-" + UUID.randomUUID().toString().substring(0, 6)).build());
        addCredentialHandler.handle(AddCredentialCommand.builder()
                .clientId(client.getId())
                .credentialType(CredentialType.API_KEY)
                .credentialValue(key)
                .build());
        long accessId = grantAccessHandler.handle(GrantAccessCommand.builder()
                .clientId(client.getId())
                .serviceId(serviceId)
                .grantReason("system test")
                .build()).getId();
        return new ApiKeyConsumer(client.getId(), accessId, key);
    }

    protected static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }
}
