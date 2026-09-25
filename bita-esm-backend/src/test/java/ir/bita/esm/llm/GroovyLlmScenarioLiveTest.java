package ir.bita.esm.llm;

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
import ir.bita.esm.route.repository.GroovyTemplateRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs the REAL production chat pipeline — {@link ChatService#sendMessage} calling out
 * through {@code LlmProviderFactory} to a live model over FreeLLMAPI — against three
 * scenarios the assistant is meant to distinguish when defining a service:
 *
 * <ol>
 *   <li><b>A — template lookup</b>: does a matching GroovyTemplate already exist for this
 *       request?</li>
 *   <li><b>B — write a new template</b>: no existing template fits, so the model should
 *       author one ({@code component_template}) instead of misusing an unrelated one.</li>
 *   <li><b>C — fill variables into a match</b>: a template fits, so the model should only
 *       extract parameter values ({@code service_groovy_config}), not write code.</li>
 * </ol>
 *
 * <p>Unlike llm-benchmark/ (the TypeScript harness that scores 50 synthetic cases against
 * a simulated tool layer), this test calls no mock: {@code ChatService}, the real
 * {@code ToolRegistry} beans, and a real H2-backed persistence layer all run exactly as in
 * production. The only substitution is the datasource (H2 in place of Postgres, for the
 * same reason as {@code GroovyBenchmarkH2Test}) and the LLM provider (FreeLLMAPI in place
 * of a paid OpenAI key).
 *
 * <p><b>Load-bearing gap this test found and fixed</b> (by reading the code, not assumed,
 * then confirmed live against a real model before fixing it): {@link
 * ir.bita.esm.llm.validation.LlmResponseValidator} used to validate the model's
 * JSON-action responses against a hardcoded {@code VALID_ACTIONS} list still holding the
 * pre-Groovy-migration action names (endpoint_template, route_template, etc.) — it was
 * missing {@code create_service} and {@code service_groovy_config} entirely. A live run
 * against gemini-3.5-flash-lite reproduced this exactly: the model correctly called
 * {@code create_service} first, the response failed validation, and the turn silently fell
 * through with no tool executed at all. Fixed by sourcing valid actions from {@code
 * ToolRegistry.getToolNames()} (the actual registered tools) instead of a second,
 * independently-maintained list — see {@code LlmResponseValidator}.
 *
 * <p><b>Still-open gap, not fixed here</b>: {@code RequestDataTool}'s only
 * {@code list_component_templates} branch queries the old {@code ComponentTemplate}
 * entity via {@code RouteQueryService}, not {@code GroovyTemplateRepository} — there is
 * currently no tool through which the model can discover which GroovyTemplates exist.
 *
 * <p>Every mutating tool (create_service, component_template, service_groovy_config)
 * requires confirmation, so a full scenario is a real multi-turn conversation — {@link
 * #converse} drives it exactly as the UI would: send the document, confirm each pending
 * tool call, and keep going until the model stops asking for one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:esm_live;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=ir.bita.esm.route.TestFriendlyH2Dialect",
        "spring.flyway.enabled=false",
        "app.llm.openai.enabled=false",
        "app.llm.boofai.enabled=false",
        "app.llm.freellmapi.enabled=true",
        // no local broker in this test. KafkaConfig declares NewTopic beans, so on startup
        // KafkaAdmin eagerly connects to try to create them, retrying against localhost:9092
        // for ~15s and flooding the log — this property stops that eager check. Excluding
        // Kafka autoconfiguration outright is NOT an option: CreateServiceHandler (reached
        // by scenario C/service_groovy_config) hard-requires a KafkaTemplate bean
        // (DomainEventPublisher, @RequiredArgsConstructor, no @ConditionalOnBean), so
        // removing the autoconfiguration would fail context startup entirely. The
        // KafkaTemplate itself stays lazy either way — it only touches the network on an
        // actual .send(), and DomainEventPublisher.publish() only logs on failure, it
        // doesn't throw, so a missing broker can't break a test that never calls it either.
        "spring.kafka.admin.auto-create=false",
})
class GroovyLlmScenarioLiveTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroovyTemplateRepository templateRepository;
    @Autowired
    private ToolExecutionRepository toolExecutionRepository;

    /**
     * {@code DomainEventPublisher.publish()} calls {@code KafkaTemplate.send()} directly
     * inside the caller's own {@code @Transactional} method (e.g. {@code
     * CreateServiceHandler.handle}) — and {@code KafkaProducer.send()} blocks synchronously
     * for up to {@code max.block.ms} (default 60s) fetching topic metadata before it even
     * returns a future. With no local broker, every {@code create_service} call hung for a
     * full minute and then threw a real {@code TimeoutException} that, caught by {@code
     * ToolRegistry.executeTool}'s blanket try/catch or not, still left the JPA persistence
     * context marked rollback-only — so {@code confirmTool}'s surrounding transaction blew
     * up with an opaque {@code UnexpectedRollbackException} instead of a clean failure.
     * This is a genuine production architecture smell (synchronous, un-timeboxed I/O inside
     * a business transaction) worth reporting on its own, but not something to silently
     * work around by changing Kafka producer config with no real broker to verify against.
     * Stubbing the publisher here removes the only thing standing in the way of actually
     * observing the LLM scenarios — the same reasoning as substituting H2 for Postgres and
     * FreeLLMAPI for a paid OpenAI key.
     */
    @MockBean
    private DomainEventPublisher domainEventPublisher;

    private Long userId;

    /**
     * {@code @Value} fields on the provider beans resolve once, at context startup — well
     * before any {@code @BeforeEach} runs — so setting {@code System.setProperty} in
     * {@code @BeforeEach} (the first attempt) was too late: {@code FreeLlmApiProvider} came
     * up with a blank api-key, {@code isAvailable()} was false for the whole run, and
     * {@code LlmProviderFactory} silently fell back to {@code BoofAiProvider} (always
     * {@code isAvailable() == true}, but its hardcoded token is expired -> 401). Registering
     * the .env values here, via {@code @DynamicPropertySource}, runs before the context is
     * created, so the real provider is the one actually configured with a key.
     */
    @DynamicPropertySource
    static void freellmapiCredentials(DynamicPropertyRegistry registry) {
        Properties props = new Properties();
        Path envFile = Path.of(System.getProperty("user.dir")).getParent().resolve(".env");
        try (FileInputStream in = new FileInputStream(envFile.toFile())) {
            for (String line : new String(in.readAllBytes()).split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) {
                    props.setProperty(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + envFile + " — is FREELLMAPI_KEY configured?", e);
        }
        registry.add("app.llm.freellmapi.api-key", () -> props.getProperty("FREELLMAPI_KEY", ""));
        registry.add("app.llm.freellmapi.base-url", () -> props.getProperty("FREELLMAPI_URL", "http://127.0.0.1:3001/v1"));
    }

    /** Same REST-proxy script bita-esb-core actually ships (see GroovyBenchmarkH2Test for
     *  the full provenance note); only the template used to test scenario C. */
    private static final String REST_PROXY_SCRIPT = """
            import io.vertx.core.Vertx
            import io.vertx.ext.web.Router
            import io.vertx.ext.web.handler.BodyHandler
            import io.vertx.ext.web.client.WebClient
            import io.vertx.ext.web.client.WebClientOptions
            import org.apache.camel.builder.RouteBuilder

            def vertx  = vertxInstance as Vertx
            def pvUrl  = new URL(pvAddress as String)
            def port   = gwPort as int
            def webClient = WebClient.create(vertx, new WebClientOptions())
            def router = Router.router(vertx)
            router.route().handler(BodyHandler.create())
            router.route("/*").handler { ctx ->
                webClient.requestAbs(ctx.request().method(), "http://${pvUrl.host}:${pvUrl.port}${ctx.request().path()}")
                    .sendBuffer(ctx.body().buffer())
                    .onSuccess { resp -> ctx.response().setStatusCode(resp.statusCode()).end(resp.body()) }
                    .onFailure { err -> ctx.response().setStatusCode(502).end(err.message) }
            }
            vertx.createHttpServer().requestHandler(router).listen(port).result()
            new RouteBuilder() { void configure() {} }
            """;

    @BeforeEach
    void setUp() {
        // a fresh number per test method — the Spring context (and its H2 instance) is
        // reused across all three @Test methods in this class, so a fixed number would
        // collide on the users.mobile_number unique constraint from the 2nd test onward
        User user = userRepository.save(User.builder()
                .mobileNumber("09" + String.format("%09d", System.nanoTime() % 1_000_000_000L))
                .fullName("Live Test User")
                .build());
        userId = user.getId();
    }

    /**
     * FreeLLMAPI's free pool has few tool-calling-capable routes, and they're shared across
     * every free user, so "429 routing_exhausted" (its own message: "9 rate-limited or on
     * cooldown, 10 model lacks tool-calling") is a real, transient condition of the pool
     * itself, not a bug in this test or in ESM. Retrying with backoff instead of failing
     * outright matches how llm-benchmark's own client already treats the same endpoint.
     */
    private ChatMessageResponse withRetry(Supplier<ChatMessageResponse> call) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                lastFailure = e;
                String msg = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                if (!msg.contains("429") && !msg.contains("Too Many Requests")) {
                    throw e;
                }
                long backoffSeconds = 15L * attempt;
                System.out.println("FreeLLMAPI pool exhausted (attempt " + attempt + "/5), retrying in "
                        + backoffSeconds + "s: " + msg);
                try {
                    Thread.sleep(backoffSeconds * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw lastFailure;
    }

    private static final List<String> REGISTERED_TOOL_NAMES = List.of(
            "ask_question", "request_data", "create_service", "service_groovy_config",
            "component_template", "list_services", "list_clients", "get_client", "complete");

    /**
     * A driven conversation, plus everything needed to assert precisely on it: the raw hop
     * responses, and the persisted {@link ToolExecution} row for every tool call this test
     * confirmed (so an assertion can check the real outcome — EXECUTED vs FAILED, and the
     * actual arguments/result persisted — not just "some response came back").
     */
    private record ScenarioRun(List<ChatMessageResponse> hops, List<ToolExecution> confirmedExecutions) {

        /** Every tool name the model invoked this conversation, in order — from native
         *  tool_calls (auto-executed, e.g. ask_question) and from pending confirmable
         *  calls alike, so it reflects every decision the model made, not just the ones
         *  that happened to need confirmation. */
        List<String> calledToolNames() {
            // ActionHandler.mapToResponse() puts every execution (including a PENDING one)
            // into toolCalls AND ALSO sets pendingToolExecution to that same execution —
            // adding both would double-count a single confirmable call, so toolCalls alone
            // (it always carries every call, pending or not) is the complete list.
            List<String> names = new ArrayList<>();
            for (ChatMessageResponse r : hops) {
                if (r.getToolCalls() != null) {
                    r.getToolCalls().forEach(tc -> names.add(tc.getToolName()));
                }
            }
            return names;
        }

        boolean called(String toolName) {
            return calledToolNames().contains(toolName);
        }

        /** The confirmed, persisted execution of a given tool (first match), or null if
         *  the model never called it (or called it but it was never confirmed). */
        ToolExecution executionOf(String toolName) {
            return confirmedExecutions.stream()
                    .filter(e -> e.getToolName().equals(toolName))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Every mutating tool (create_service, component_template, service_groovy_config)
     * requires confirmation, so a single {@code sendMessage} only ever gets as far as the
     * FIRST step (create_service) — the rest of the flow ({@link ChatService#confirmTool})
     * executes the tool, feeds its result back to the model as a TOOL message, and asks the
     * model again. This drives that full loop for real, confirming every pending tool call
     * until the model stops asking for one (or the hop limit trips, so a genuine infinite
     * back-and-forth fails loudly instead of hanging the suite), and records the persisted
     * {@link ToolExecution} for each confirmed call so scenario-specific assertions can
     * check what actually got executed, not just what the model said it wanted to do.
     */
    private ScenarioRun converse(String label, String document) {
        // gemini-3.5-flash-lite (used in earlier runs) hit a ~10h FreeLLMAPI cooldown after
        // repeated use; "auto" is confirmed live (just now, via curl) to route to a
        // tool-calling-capable model (groq/openai/gpt-oss-120b) and actually respond.
        var session = chatService.createSession(userId, CreateSessionRequest.builder()
                .title("live-scenario")
                .modelName("auto")
                .build());

        List<ChatMessageResponse> hops = new ArrayList<>();
        List<ToolExecution> confirmedExecutions = new ArrayList<>();

        ChatMessageResponse r = withRetry(() -> chatService.sendMessage(session.getId(), userId,
                SendMessageRequest.builder().content(document).build()));
        hops.add(r);

        int hop = 1;
        while (r.getPendingToolExecution() != null && hop <= 5) {
            dump(label + " — hop " + hop, r);
            String toolCallId = r.getPendingToolExecution().getId();
            r = withRetry(() -> chatService.confirmTool(session.getId(), userId,
                    ConfirmToolRequest.builder().toolCallId(toolCallId).confirmed(true).build()));
            hops.add(r);
            toolExecutionRepository.findByToolCallId(toolCallId).ifPresent(confirmedExecutions::add);
            hop++;
        }
        dump(label + " — final", r);
        return new ScenarioRun(hops, confirmedExecutions);
    }

    private void dump(String label, ChatMessageResponse r) {
        System.out.println("\n===== " + label + " =====");
        System.out.println("assistant content: " + r.getContent());
        if (r.getToolCalls() != null) {
            r.getToolCalls().forEach(tc -> System.out.println(
                    "tool=" + tc.getToolName() + " args=" + tc.getArguments()
                            + " status=" + tc.getStatus() + " result=" + tc.getResult()
                            + " error=" + tc.getErrorMessage()));
        }
        if (r.getPendingToolExecution() != null) {
            var p = r.getPendingToolExecution();
            System.out.println("PENDING tool=" + p.getToolName() + " args=" + p.getArguments());
        }
        if ((r.getToolCalls() == null || r.getToolCalls().isEmpty()) && r.getPendingToolExecution() == null) {
            System.out.println("(no tool call this turn — either the model answered in plain text, or it hit "
                    + "the still-open RequestDataTool/GroovyTemplate-discovery gap noted in the class javadoc)");
        }
    }

    /**
     * All three @Test methods share one Spring context and one H2 instance (no rollback
     * between them), so a fixed template name collides on {@code groovy_template.name}'s
     * unique constraint from the second seeding call onward.
     */
    private String uniqueName(String base) {
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------------ scenario A
    @Test
    @DisplayName("A — given a matching GroovyTemplate exists, does the model try to find it?")
    void scenarioA_templateLookup() {
        templateRepository.save(GroovyTemplate.builder()
                .name(uniqueName("rest-proxy"))
                .description("REST-to-REST proxy over Vert.x")
                .scriptText(REST_PROXY_SCRIPT)
                .build());

        String document = """
                موضوع: درخواست راه‌اندازی سرویس «استعلام کد پستی» روی گذرگاه بیتا

                با سلام، این یک API مبتنی بر REST/JSON است که باید بدون تغییر از طریق بیتا منتشر شود.

                مشخصات فنی:
                - نام فنی سرویس postal-code، نسخهٔ v1، و مجموعهٔ post است.
                - آدرس سرویس: http://10.10.10.10:8080/api/postal-code
                - پورت اختصاصی گذرگاه: 18090
                - مسیر انتشار روی گذرگاه: /esb/post/postal-code/v1
                """;

        ScenarioRun run = converse("A: template lookup", document);
        assertOnlyRealTools(run);

        // the assistant must not write new code when a matching template already exists
        org.assertj.core.api.Assertions.assertThat(run.calledToolNames())
                .as("scenario A must not author a new template when rest-proxy already matches")
                .doesNotContain("component_template");

        // and it must actually reach the fill-variables step, not just talk about it or
        // stall out on a silently-dropped/invalid action
        ToolExecution config = run.executionOf("service_groovy_config");
        org.assertj.core.api.Assertions.assertThat(config)
                .as("scenario A must reach service_groovy_config: %s", run.calledToolNames())
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(config.getStatus())
                .as("service_groovy_config must actually execute, not fail: %s", config.getErrorMessage())
                .isEqualTo(ToolExecutionStatus.EXECUTED);
    }

    // ------------------------------------------------------------------ scenario B
    @Test
    @DisplayName("B — given NO template fits, does the model write a new one instead of misusing rest-proxy?")
    void scenarioB_writeNewTemplate() {
        // MQTT was the original mismatch case, but the thesis explicitly scopes protocol
        // coverage to SOAP+WS-Security and REST ("دامنهٔ پروتکل ... GraphQL و gRPC خارج از
        // محدودهٔ نسخهٔ اولیه است" — ch.2), so an MQTT case would sit outside that stated
        // boundary. Swapped to an in-scope mismatch instead: the only seeded template is a
        // plain SOAP passthrough with explicitly NO WS-Security, and the request needs
        // WS-Security — same "nothing actually fits" shape, without reaching past what the
        // thesis claims to cover.
        templateRepository.save(GroovyTemplate.builder()
                .name(uniqueName("soap-passthrough"))
                .description("Plain SOAP passthrough, no WS-Security")
                .scriptText("// soap passthrough placeholder — not relevant to this request")
                .build());

        String document = """
                موضوع: درخواست راه‌اندازی سرویس «استعلام موجودی» روی گذرگاه بیتا

                این یک سرویس SOAP است که باید با WS-Security 1.1 (امضای دیجیتال پیام،
                گواهی X.509) منتشر شود؛ ترافیک بدون امضا توسط سیستم مقصد رد می‌شود.

                مشخصات فنی:
                - نام فنی سرویس balance-inquiry، نسخهٔ v1، و مجموعهٔ bank است.
                - آدرس WSDL سرویس: http://10.30.30.30:8080/ws/balance?wsdl
                - نیازمند امضای WS-Security با گواهی X.509 سازمان مبدا است.
                """;

        ScenarioRun run = converse(
                "B: no template fits (needs WS-Security, only a no-security SOAP template exists) — "
                        + "expect component_template or ask_question, not service_groovy_config on soap-passthrough",
                document);
        assertOnlyRealTools(run);

        // the hard invariant regardless of which of the two reasonable paths (write a new
        // template, or ask how MQTT should bridge to HTTP) the model picks: it must never
        // fabricate a match to the unrelated soap-passthrough template
        org.assertj.core.api.Assertions.assertThat(run.calledToolNames())
                .as("scenario B must never call service_groovy_config — no template actually fits")
                .doesNotContain("service_groovy_config");

        // and it must actually engage, not silently drop the turn
        org.assertj.core.api.Assertions.assertThat(run.calledToolNames())
                .as("scenario B must call a real tool, not go silent")
                .isNotEmpty();
    }

    // ------------------------------------------------------------------ scenario C
    @Test
    @DisplayName("C — given a matching template AND a complete document, does the model only fill variables?")
    void scenarioC_fillVariablesOnly() {
        templateRepository.save(GroovyTemplate.builder()
                .name(uniqueName("rest-proxy"))
                .description("REST-to-REST proxy over Vert.x")
                .scriptText(REST_PROXY_SCRIPT)
                .build());

        String document = """
                موضوع: درخواست راه‌اندازی سرویس «اعتبارسنجی شبا» روی گذرگاه بیتا

                این یک API مبتنی بر REST/JSON است که باید بدون تغییر از طریق بیتا منتشر شود.

                مشخصات فنی:
                - نام فنی سرویس iban-validate، نسخهٔ v1، و مجموعهٔ bank است.
                - آدرس سرویس: http://10.5.5.5:8080/api/iban
                - پورت اختصاصی گذرگاه: 18095
                - مسیر انتشار روی گذرگاه: /esb/bank/iban-validate/v1
                """;

        ScenarioRun run = converse(
                "C: exact template match — expect service_groovy_config with pvAddress/gwPort only, no new code",
                document);
        assertOnlyRealTools(run);

        // the document is complete and a template matches exactly — nothing to ask, nothing
        // to write
        org.assertj.core.api.Assertions.assertThat(run.calledToolNames())
                .as("scenario C must not write new code when rest-proxy already matches")
                .doesNotContain("component_template");
        org.assertj.core.api.Assertions.assertThat(run.calledToolNames())
                .as("scenario C must not ask a clarifying question — the document is already complete")
                .doesNotContain("ask_question");

        ToolExecution config = run.executionOf("service_groovy_config");
        org.assertj.core.api.Assertions.assertThat(config)
                .as("scenario C must reach service_groovy_config: %s", run.calledToolNames())
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(config.getStatus())
                .as("service_groovy_config must actually execute, not fail: %s", config.getErrorMessage())
                .isEqualTo(ToolExecutionStatus.EXECUTED);

        // not just "some tool executed" — the extracted variable values must actually
        // contain the address this specific document named, proving real parameter
        // extraction happened rather than a lucky/empty call
        @SuppressWarnings("unchecked")
        Map<String, Object> variableValues = (Map<String, Object>) config.getArguments().get("variableValues");
        org.assertj.core.api.Assertions.assertThat(variableValues)
                .as("service_groovy_config must be called with variableValues, not left empty")
                .isNotNull().isNotEmpty();
        org.assertj.core.api.Assertions.assertThat(variableValues.values())
                .as("extracted variables must contain the document's own backend address: %s", variableValues)
                .anyMatch(v -> String.valueOf(v).contains("10.5.5.5"));
    }

    /** Whatever the model called across the whole conversation, it must be a real,
     *  registered tool — never a hallucinated name. */
    private void assertOnlyRealTools(ScenarioRun run) {
        org.assertj.core.api.Assertions.assertThat(run.calledToolNames())
                .as("every tool the model called must be a real, registered tool")
                .allMatch(REGISTERED_TOOL_NAMES::contains);
    }
}
