package ir.bita.system.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import ir.bita.common.domain.VariableType;
import ir.bita.esm.auth.entity.User;
import ir.bita.esm.auth.repository.UserRepository;
import ir.bita.esm.llm.dto.ChatMessageResponse;
import ir.bita.esm.llm.dto.ConfirmToolRequest;
import ir.bita.esm.llm.dto.CreateSessionRequest;
import ir.bita.esm.llm.dto.SendMessageRequest;
import ir.bita.esm.llm.entity.ToolExecution;
import ir.bita.esm.llm.entity.ToolExecutionStatus;
import ir.bita.esm.llm.repository.ToolExecutionRepository;
import ir.bita.esm.llm.service.ChatService;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.entity.ServiceGroovyConfig;
import ir.bita.esm.route.repository.ServiceGroovyConfigRepository;
import ir.bita.system.support.ws.TestKeyStoreGenerator;
import ir.bita.system.support.ws.WsSecurityParties;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Drives one benchmark case through the real system: the production ChatService talking to a
 * live model, every proposal handled the way a reviewer would, the resulting config loaded by a
 * real ESB, and the gateway actually called. Results are appended to
 * {@code target/benchmark/<run>.jsonl} so a run can be reported case by case.
 */
@TestPropertySource(properties = {
        // own database: other system tests seed their own templates, which must not show up in
        // the catalog the model chooses from
        "spring.datasource.url=jdbc:h2:mem:benchmark;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.llm.freellmapi.enabled=true",
})
@Import(LlmCallRecorder.class)
public abstract class BenchmarkBase extends SystemTestBase {

    private static final Properties ENV = loadDotEnv();
    private static final String RUN_ID = Instant.now().toString().replace(":", "-");

    @DynamicPropertySource
    static void freellmapi(DynamicPropertyRegistry registry) {
        registry.add("app.llm.freellmapi.api-key", () -> ENV.getProperty("FREELLMAPI_KEY", ""));
        registry.add("app.llm.freellmapi.base-url", () -> ENV.getProperty("FREELLMAPI_URL", ""));
    }

    @Autowired
    protected ChatService chatService;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected ToolExecutionRepository toolExecutionRepository;
    @Autowired
    protected ServiceGroovyConfigRepository configRepository;
    @Autowired
    protected ir.bita.esm.route.service.ServiceConfigVersionService versionService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * Each case starts from the same state: the template catalog, with no services. Benchmark
     * documents reuse organisations (collections) and service names across cases, and
     * service_collection's name/base_path are unique in the database, so what one case created
     * is removed before the next.
     */
    @org.junit.jupiter.api.AfterEach
    void removeServicesCreatedByThisCase() {
        for (String table : List.of("service_config_version", "service_groovy_config", "service_access",
                "service", "service_collection")) {
            jdbc.update("delete from " + table);
        }
    }

    /** The six templates every case is given; nothing else may be in the catalog the model sees. */
    public static final List<String> CATALOG = List.of("rest-proxy", "rest-proxy-rate-limit", "soap-passthrough",
            "soap-ws-security", "rest-to-soap-bridge", "soap-backend-basic-auth");

    /**
     * The Spring context (and its database) is shared by all test classes, and the non-LLM tests
     * seed their own templates; a template one case created (groovy_template) must not be offered
     * to the next either. So before a case: no services, and exactly {@link #CATALOG}.
     */
    private void resetCatalog() {
        removeServicesCreatedByThisCase();
        jdbc.update("delete from groovy_template where name not in ("
                + String.join(",", CATALOG.stream().map(n -> "'" + n + "'").toList()) + ")");
    }

    protected static final List<String> MUTATING = List.of("create_service", "service_groovy_config", "component_template",
            "groovy_template");

    /** What happened in one case, in the order it happened. */
    public static final class Run {
        public final BenchmarkCase c;
        public final String model;
        public final List<String> events = new ArrayList<>();
        public final List<ToolExecution> executed = new ArrayList<>();
        public final List<Map<String, Object>> rejected = new ArrayList<>();
        public boolean asked;
        public Long serviceId;
        public long startedAtMs;
        public Long servedAfterMs;
        /** Time spent waiting out provider rate limits/outages — not the system's time. */
        public long backoffMs;
        /** Replies whose action format was broken and re-sent once by the model (reported, not hidden). */
        public int formatRepairs;

        public Run(BenchmarkCase c, String model) {
            this.c = c;
            this.model = model;
        }

        public boolean executed(String tool) {
            return executed.stream().anyMatch(e -> e.getToolName().equals(tool)
                    && e.getStatus() == ToolExecutionStatus.EXECUTED);
        }
    }

    /**
     * Stands in for the human reviewer: given a proposal (tool name + arguments), says whether to
     * confirm it. {@link #confirmAll} is the reviewer who trusts every proposal.
     */
    protected interface Reviewer {
        boolean approve(Run run, String tool, Map<String, Object> args);
    }

    protected static final Reviewer confirmAll = (run, tool, args) -> true;

    // ------------------------------------------------------------------ catalog

    /** The six templates the model chooses from, with the benchmark's own names and descriptions. */
    /** No templates at all: the ablation where the model has to write every service's script itself. */
    protected void emptyCatalog() {
        resetCatalog();
        jdbc.update("delete from groovy_template");
        catalogReset = true;
    }

    /** JUnit makes one instance per test, so this is "already reset in this test". */
    private boolean catalogReset;

    protected Map<String, GroovyTemplate> seedCatalog() throws IOException {
        // once per test: a test that defines two services (R1) must not wipe the first
        if (!catalogReset) {
            resetCatalog();
            catalogReset = true;
        }
        Map<String, GroovyTemplate> catalog = new LinkedHashMap<>();
        var gw = List.of(variable("gwPort", "پورت گیت‌وی", VariableType.PORT),
                variable("gwPath", "مسیر Ingress", VariableType.STRING));
        var soap = List.of(variable("pvAddress", "آدرس سرویس SOAP", VariableType.STRING),
                variable("pvWsdlUri", "آدرس WSDL", VariableType.STRING));

        catalog.put("rest-proxy", catalogTemplate("rest-proxy", "پروکسی ساده REST به REST با کنترل دسترسی BITA",
                concat(List.of(variable("pvAddress", "آدرس سرویس ارائه‌دهنده", VariableType.STRING)), gw)));
        catalog.put("rest-proxy-rate-limit", catalogTemplate("rest-proxy-rate-limit",
                "پروکسی REST با محدودیت نرخ درخواست برای هر کلاینت",
                concat(List.of(variable("pvAddress", "آدرس سرویس ارائه‌دهنده", VariableType.STRING)), gw,
                        List.of(variable("rateLimitPerMinute", "حداکثر درخواست در دقیقه", VariableType.INT)))));
        catalog.put("soap-passthrough", catalogTemplate("soap-passthrough", "عبور مستقیم SOAP بدون WS-Security",
                concat(soap, gw)));
        catalog.put("soap-ws-security", catalogTemplate("soap-ws-security",
                "سرویس SOAP با WS-Security 1.1 (امضا و در صورت نیاز رمزنگاری با گواهی X.509)",
                concat(soap, gw, List.of(
                        variable("securityPolicy", "سیاست امنیتی (Timestamp+Signature یا Timestamp+Signature+Encryption)", VariableType.STRING),
                        variable("signaturePropsFile", "فایل properties امضا", VariableType.STRING),
                        variable("truststorePropsFile", "فایل properties truststore", VariableType.STRING)))));
        catalog.put("rest-to-soap-bridge", catalogTemplate("rest-to-soap-bridge",
                "دریافت REST/JSON از کلاینت و فراخوانی یک operation در سرویس SOAP",
                concat(soap, List.of(variable("soapOperation", "نام operation", VariableType.STRING)), gw)));
        catalog.put("soap-backend-basic-auth", catalogTemplate("soap-backend-basic-auth",
                "سرویس SOAP که خود ارائه‌دهنده با Basic Auth محافظت شده است (اعتبارنامهٔ downstream)",
                concat(soap, gw, List.of(
                        variable("backendUsername", "نام کاربری ارائه‌دهنده", VariableType.STRING),
                        variable("backendPassword", "رمز ارائه‌دهنده", VariableType.SECRET)))));
        return catalog;
    }

    private GroovyTemplate catalogTemplate(String name, String description,
                                           List<GroovyTemplate.VariableMetadata> vars) throws IOException {
        var existing = templateRepository.findByNameAndDeletedFalse(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        return templateRepository.save(GroovyTemplate.builder()
                .name(name).description(description).scriptText(script(name)).variables(new ArrayList<>(vars)).build());
    }

    private String script(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/templates/" + name + ".groovy")) {
            return in == null
                    ? "throw new IllegalStateException('template " + name + " has no script yet')"
                    : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SafeVarargs
    private static <T> List<T> concat(List<T>... parts) {
        List<T> all = new ArrayList<>();
        for (List<T> p : parts) {
            all.addAll(p);
        }
        return all;
    }

    // ------------------------------------------------------------------ conversation

    /** A conversation driven turn by turn, for tests whose script is not "confirm everything". */
    public final class Conversation {
        public final Run run;
        private final Long sessionId;
        private final Long userId;
        public ChatMessageResponse last;

        private Conversation(Run run, Long sessionId, Long userId) {
            this.run = run;
            this.sessionId = sessionId;
            this.userId = userId;
        }

        public ChatMessageResponse send(String text) {
            run.events.add("user " + text);
            last = llm(run, () -> chatService.sendMessage(sessionId, userId,
                    SendMessageRequest.builder().content(text).build()));
            record(run, last);
            return last;
        }

        /** Answers the pending proposal; returns the model's next turn. */
        public ChatMessageResponse answerPending(boolean confirm) {
            var pending = last.getPendingToolExecution();
            if (pending == null) {
                throw new AssertionError("no pending proposal; events: " + run.events);
            }
            String callId = pending.getId();
            run.events.add((confirm ? "confirm " : "reject ") + pending.getToolName() + " " + pending.getArguments());
            last = llm(run, () -> chatService.confirmTool(sessionId, userId,
                    ConfirmToolRequest.builder().toolCallId(callId).confirmed(confirm).build()));
            toolExecutionRepository.findByToolCallId(callId).ifPresent(e -> {
                run.executed.add(e);
                if ("create_service".equals(e.getToolName()) && e.getResult() != null
                        && e.getResult().get("serviceId") != null) {
                    run.serviceId = ((Number) e.getResult().get("serviceId")).longValue();
                }
            });
            record(run, last);
            return last;
        }

        /** Confirms pending proposals of {@code tool}'s predecessors until {@code tool} is the pending one. */
        public boolean confirmUntil(String tool, int maxHops) {
            for (int i = 0; i < maxHops; i++) {
                var pending = last.getPendingToolExecution();
                if (pending == null) {
                    return false;
                }
                if (tool.equals(pending.getToolName())) {
                    return true;
                }
                answerPending(true);
            }
            return false;
        }
    }

    protected Conversation startConversation(String label) {
        String model = ModelProbe.model(ENV.getProperty("FREELLMAPI_URL", ""), ENV.getProperty("FREELLMAPI_KEY", ""));
        BenchmarkCase pseudo = new BenchmarkCase(label, "-", "-", null, "-", "-", "-", "-", Map.of(), "", null);
        Run run = new Run(pseudo, model);
        User user = userRepository.save(User.builder()
                .mobileNumber("09" + String.format("%09d", System.nanoTime() % 1_000_000_000L))
                .fullName("Scenario " + label).build());
        var session = chatService.createSession(user.getId(), CreateSessionRequest.builder()
                .title(label).modelName(model).build());
        run.startedAtMs = System.currentTimeMillis();
        return new Conversation(run, session.getId(), user.getId());
    }

    protected Run converse(BenchmarkCase c, Reviewer reviewer, boolean answerFollowUp) {
        String model = ModelProbe.model(ENV.getProperty("FREELLMAPI_URL", ""), ENV.getProperty("FREELLMAPI_KEY", ""));
        Run run = new Run(c, model);
        User user = userRepository.save(User.builder()
                .mobileNumber("09" + String.format("%09d", System.nanoTime() % 1_000_000_000L))
                .fullName("Benchmark " + c.id()).build());
        var session = chatService.createSession(user.getId(), CreateSessionRequest.builder()
                .title(c.id()).modelName(model).build());

        run.startedAtMs = System.currentTimeMillis();
        ChatMessageResponse r = llm(run, () -> chatService.sendMessage(session.getId(), user.getId(),
                SendMessageRequest.builder().content(c.document()).build()));
        boolean followUpSent = false;

        for (int hop = 0; hop < 8; hop++) {
            record(run, r);
            var pending = r.getPendingToolExecution();
            if (pending != null) {
                boolean approve = reviewer.approve(run, pending.getToolName(), pending.getArguments());
                String callId = pending.getId();
                run.events.add((approve ? "confirm " : "reject ") + pending.getToolName() + " " + pending.getArguments());
                if (!approve) {
                    run.rejected.add(Map.of("tool", pending.getToolName(), "args", pending.getArguments()));
                }
                r = llm(run, () -> chatService.confirmTool(session.getId(), user.getId(),
                        ConfirmToolRequest.builder().toolCallId(callId).confirmed(approve).build()));
                toolExecutionRepository.findByToolCallId(callId).ifPresent(e -> {
                    run.executed.add(e);
                    if ("create_service".equals(e.getToolName()) && e.getResult() != null
                            && e.getResult().get("serviceId") != null) {
                        run.serviceId = ((Number) e.getResult().get("serviceId")).longValue();
                    }
                });
                if (!approve) {
                    break;
                }
                continue;
            }
            if (run.asked && answerFollowUp && !followUpSent && c.followUp() != null) {
                followUpSent = true;
                run.events.add("user " + c.followUp());
                r = llm(run, () -> chatService.sendMessage(session.getId(), user.getId(),
                        SendMessageRequest.builder().content(c.followUp()).build()));
                continue;
            }
            break;
        }
        return run;
    }

    protected void record(Run run, ChatMessageResponse r) {
        if (r.isFormatRepaired()) {
            run.formatRepairs++;
            run.events.add("format-repaired");
        }
        boolean anyTool = false;
        if (r.getToolCalls() != null) {
            for (var tc : r.getToolCalls()) {
                anyTool = true;
                run.events.add("tool " + tc.getToolName() + " " + tc.getStatus() + " " + tc.getArguments());
                if ("ask_question".equals(tc.getToolName())) {
                    run.asked = true;
                }
            }
        }
        if (!anyTool && r.getPendingToolExecution() == null) {
            // a plain-text turn: the model talked instead of acting (a question or an explanation)
            run.asked = true;
            run.events.add("text " + abbreviate(r.getContent()));
        }
    }

    /** A live model call; a provider outage aborts the case (skipped), it does not fail it. */
    protected ChatMessageResponse llm(Run run, Supplier<ChatMessageResponse> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                String msg = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                boolean outage = msg.contains("429") || msg.contains("Too Many Requests") || msg.contains("502")
                        || msg.contains("503") || msg.contains("Bad Gateway") || msg.contains("timed out")
                        || msg.contains("I/O error") || msg.contains("failed to respond") || msg.contains("Connection refused");
                if (!outage) {
                    throw e;
                }
                last = e;
                try {
                    run.backoffMs += 15_000L * attempt;
                    Thread.sleep(15_000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        Assumptions.abort("LLM provider unavailable: " + (last == null ? "" : last.getMessage()));
        return null;
    }

    // ------------------------------------------------------------------ execution

    /** Keys for the WS-Security case currently running (one set per case). */
    protected WsSecurityParties wssParties;

    /** Starts the provider the case's service should reach, on {@code port}. */
    protected AutoCloseable startProvider(BenchmarkCase c, String marker, int port) throws Exception {
        if (c.templateName().startsWith("rest-proxy")) {
            startBackend(marker, port);
            return () -> {
            };
        }
        if ("soap-ws-security".equals(c.templateName())) {
            Path dir = Files.createTempDirectory("bench-wss-" + c.id() + "-");
            wssParties = new WsSecurityParties(dir);
            var ks = wssParties.keyStores();
            // the property files the document names exist next to the gateway's keystore, under
            // exactly those names — a wrongly extracted name therefore really fails
            Path secrets = Path.of(ks.gwKeystorePropsPath()).getParent();
            Files.copy(Path.of(ks.gwKeystorePropsPath()), secrets.resolve(c.variables().get("signaturePropsFile")),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(Path.of(ks.gwOutgoingTrustPropsPath()), secrets.resolve(c.variables().get("truststorePropsFile")),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            org.apache.cxf.endpoint.Server server = wssParties.startProvider(c.variables().get("pvAddress"),
                    c.variables().get("securityPolicy").contains("Encryption"));
            return server::stop;
        }
        return new SoapStubProvider(port, marker,
                c.variables().get("backendUsername"), c.variables().get("backendPassword"));
    }

    /** What a consumer got calling the service through its gateway. */
    public record GatewayResult(boolean reachedProvider, String detail) {
    }

    /**
     * Loads the service's config on a real ESB and calls its gateway the way a consumer would:
     * plain HTTP for REST, a SOAP envelope for SOAP, and for WS-Security a consumer organisation
     * registered in ESM that signs and encrypts its call.
     */
    protected GatewayResult callThroughGateway(Run run, String templateName, Map<String, Object> vars,
                                               String marker) throws Exception {
        int gwPort = Integer.parseInt(String.valueOf(vars.get("gwPort")));
        String gwPath = String.valueOf(vars.get("gwPath"));

        if ("soap-ws-security".equals(templateName)) {
            grantConsumer(run.serviceId, "consumer-" + run.c.id(), wssParties.consumerCertificatePem());
            startEsb(run.serviceId, wssParties.keyStores().gwKeystorePropsPath(), TestKeyStoreGenerator.PASSWORD);
            try {
                String answer = wssParties.consumer("http://127.0.0.1:" + gwPort + gwPath).echo(run.c.id(), 0);
                return new GatewayResult(("I get" + run.c.id()).equals(answer), answer);
            } catch (Exception e) {
                return new GatewayResult(false, e.getMessage());
            }
        }

        String apiKey = grantApiKeyConsumer(run.serviceId, "consumer-" + run.c.id());
        startEsb(run.serviceId);
        Vertx v = Vertx.vertx();
        try {
            WebClient http = WebClient.create(v);
            var resp = switch (templateName) {
                case "rest-proxy", "rest-proxy-rate-limit" -> http.get(gwPort, "127.0.0.1", gwPath + "/ping")
                        .putHeader("X-API-Key", apiKey).send();
                case "rest-to-soap-bridge" -> http.post(gwPort, "127.0.0.1", gwPath)
                        .putHeader("X-API-Key", apiKey)
                        .sendJsonObject(new JsonObject().put("requestId", run.c.id()));
                default -> http.post(gwPort, "127.0.0.1", gwPath)
                        .putHeader("X-API-Key", apiKey)
                        .putHeader("Content-Type", "text/xml; charset=utf-8")
                        .sendBuffer(Buffer.buffer("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                                + " xmlns:ns=\"" + SoapStubProvider.NAMESPACE + "\"><soapenv:Body><ns:Ping><requestId>"
                                + run.c.id() + "</requestId></ns:Ping></soapenv:Body></soapenv:Envelope>"));
            };
            HttpResponse<Buffer> r = resp.toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
            String body = r.bodyAsString();
            return new GatewayResult(r.statusCode() == 200 && body != null && body.contains(marker),
                    r.statusCode() + " " + (body == null ? "" : body.substring(0, Math.min(200, body.length()))));
        } finally {
            v.close();
        }
    }

    /**
     * Starts the ESB for a soap-ws-security service defined by {@link #defineService}: registers the
     * consumer organisation's certificate in ESM, grants it access, and gives the ESB the
     * gateway's keystore.
     */
    protected void startWsSecurityEsb(DefinedService s) throws Exception {
        grantConsumer(s.serviceId(), "wss-consumer", wssParties.consumerCertificatePem());
        startEsb(s.serviceId(), wssParties.keyStores().gwKeystorePropsPath(), TestKeyStoreGenerator.PASSWORD);
    }

    /** A consumer that signs and encrypts its calls to the service's gateway (one per thread). */
    protected ir.bita.system.support.ws.EchoService wsSecurityConsumer(DefinedService s) {
        return wssParties.consumer("http://127.0.0.1:" + s.gwPort() + s.gwPath());
    }

    /**
     * A service defined from a benchmark document, where its parameters came from, and the API key
     * of a consumer organisation granted access to it.
     */
    public record DefinedService(long serviceId, Map<String, Object> variables, String source, String apiKey) {
        public int gwPort() {
            return Integer.parseInt(String.valueOf(variables.get("gwPort")));
        }

        public String gwPath() {
            return String.valueOf(variables.get("gwPath"));
        }
    }

    /**
     * Brings up the service a benchmark document asks for, with the LLM supplying its parameters —
     * live if a model is reachable, otherwise the parameters a live model produced for the same
     * document in the most recent B1 run ({@code source} says which, and the provider origin is
     * moved to this run's stub the same way the document's is). Only the parameters come from
     * the model; everything after this is the system being measured.
     */
    protected DefinedService defineService(String caseId, int pvPort, String marker) throws Exception {
        return defineService(caseId, pvPort, marker, true);
    }

    /** {@code startProvider=false}: the test runs its own provider on {@code pvPort}. */
    protected DefinedService defineService(String caseId, int pvPort, String marker, boolean startProvider)
            throws Exception {
        seedCatalog();
        BenchmarkCase original = BenchmarkCase.byId(caseId);
        String origin = "http://127.0.0.1:" + pvPort;
        BenchmarkCase c = original.withProviderOrigin(origin);
        if (startProvider) {
            startProvider(c, marker, pvPort);
        }
        try {
            Run run = converse(c, confirmAll, false);
            ServiceGroovyConfig config = configOf(run);
            if (config != null) {
                return new DefinedService(run.serviceId, new HashMap<>(config.getVariableValues()),
                        "live:" + run.model, grantApiKeyConsumer(run.serviceId, "consumer-" + caseId));
            }
            throw new IllegalStateException("live model produced no configuration: " + run.events);
        } catch (org.opentest4j.TestAbortedException | IllegalStateException unavailable) {
            Map<String, Object> recorded = recordedVariables(caseId);
            if (recorded == null && Boolean.getBoolean("bench.allowGroundTruth")) {
                // development only: lets the measurement code be exercised with no model and no
                // recorded run; the report says so, and a measured run never sets this flag
                recorded = new HashMap<>(original.variables());
                recorded.put("__origin", original.providerOrigin());
                recorded.put("__file", "GROUND-TRUTH (dev only, not model output)");
            }
            if (recorded == null) {
                throw unavailable;
            }
            String from = String.valueOf(recorded.remove("__origin"));
            String fileTag = String.valueOf(recorded.remove("__file"));
            recorded.put("__file", fileTag);
            Map<String, Object> vars = new HashMap<>();
            recorded.forEach((k, v) -> {
                if (!k.startsWith("__")) {
                    vars.put(k, String.valueOf(v).replace(from, origin));
                }
            });
            Map<String, Object> created = executeTool("create_service", Map.of(
                    "serviceName", c.serviceName(), "serviceVersion", c.serviceVersion(),
                    "collectionName", c.collectionName() + "-" + java.util.UUID.randomUUID().toString().substring(0, 4)));
            long serviceId = ((Number) created.get("serviceId")).longValue();
            executeTool("service_groovy_config", Map.of(
                    "serviceId", serviceId,
                    "groovyTemplateId", templateRepository.findByNameAndDeletedFalse(c.templateName()).orElseThrow().getId(),
                    "variableValues", vars));
            String file = String.valueOf(recorded.getOrDefault("__file", "B1"));
            return new DefinedService(serviceId, vars,
                    file.startsWith("GROUND-TRUTH") ? file : "recorded:" + file,
                    grantApiKeyConsumer(serviceId, "consumer-" + caseId));
        }
    }

    /** The variables a live model produced for {@code caseId} in the most recent B1 run that has them. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> recordedVariables(String caseId) throws IOException {
        Path dir = Path.of("target", "benchmark");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        List<Path> files;
        try (var list = Files.list(dir)) {
            files = list.filter(p -> p.getFileName().toString().startsWith("B1-"))
                    .sorted(java.util.Comparator.comparing(Path::toString).reversed()).toList();
        }
        ObjectMapper json = new ObjectMapper();
        for (Path f : files) {
            for (String line : Files.readAllLines(f)) {
                Map<String, Object> d = json.readValue(line, Map.class);
                if (!caseId.equals(d.get("case")) || !Boolean.TRUE.equals(d.get("variablesCorrect"))) {
                    continue;
                }
                Map<String, Object> vars = d.get("executedVariables") instanceof Map<?, ?> m
                        ? new HashMap<>((Map<String, Object>) m) : fromEvents((List<String>) d.get("events"));
                if (vars == null) {
                    continue;
                }
                String pv = String.valueOf(vars.get("pvAddress"));
                int slash = pv.indexOf('/', pv.indexOf("://") + 3);
                vars.put("__origin", slash < 0 ? pv : pv.substring(0, slash));
                vars.put("__file", f.getFileName().toString());
                return vars;
            }
        }
        return null;
    }

    /** Older result lines only kept the confirmed call as text: "confirm service_groovy_config {..variableValues={k=v, ..}..}". */
    private static Map<String, Object> fromEvents(List<String> events) {
        for (String e : events) {
            if (!e.startsWith("confirm service_groovy_config")) {
                continue;
            }
            var m = java.util.regex.Pattern.compile("variableValues=\\{([^}]*)}").matcher(e);
            if (m.find()) {
                Map<String, Object> vars = new HashMap<>();
                for (String kv : m.group(1).split(", ")) {
                    int eq = kv.indexOf('=');
                    if (eq > 0) {
                        vars.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
                    }
                }
                return vars;
            }
        }
        return null;
    }

    protected ServiceGroovyConfig configOf(Run run) {
        return run.serviceId == null ? null : configRepository.findByServiceId(run.serviceId).orElse(null);
    }

    // ------------------------------------------------------------------ reporting

    protected static void report(String suite, Run run, Map<String, Object> outcome) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("case", run.c.id());
        line.put("level", run.c.level());
        line.put("expectedTemplate", run.c.templateName());
        line.put("expectedBehavior", run.c.expectedBehavior());
        line.put("model", run.model);
        line.put("answeredBy", new ArrayList<>(LlmCallRecorder.ANSWERED_BY));
        line.put("formatRepairs", run.formatRepairs);
        line.putAll(outcome);
        line.put("events", run.events);
        try {
            Path dir = Path.of("target", "benchmark");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(suite + "-" + RUN_ID + ".jsonl"),
                    new ObjectMapper().writeValueAsString(line) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        LlmCallRecorder.ANSWERED_BY.clear();
    }

    /** Result line for the tests that measure a defined service rather than a conversation. */
    protected static void report(String suite, DefinedService service, Map<String, Object> outcome) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("serviceId", service.serviceId());
        line.put("variables", service.variables());
        line.putAll(outcome);
        try {
            Path dir = Path.of("target", "benchmark");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(suite + "-" + RUN_ID + ".jsonl"),
                    new ObjectMapper().writeValueAsString(line) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static boolean containsAny(Object value, Predicate<String> test) {
        return value != null && test.test(String.valueOf(value).toLowerCase());
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private static Properties loadDotEnv() {
        Properties props = new Properties();
        Path env = Path.of(System.getProperty("user.dir")).resolveSibling(".env");
        if (!Files.exists(env)) {
            env = Path.of(System.getProperty("user.dir")).resolve(".env");
        }
        try (FileInputStream in = new FileInputStream(env.toFile())) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) {
                    props.setProperty(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + env + " — FREELLMAPI_KEY/FREELLMAPI_URL required", e);
        }
        return props;
    }

    protected static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
