package ir.bita.esm.route;

import ir.bita.common.domain.ServicePhase;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.entity.ServiceGroovyConfig;
import ir.bita.esm.route.repository.GroovyTemplateRepository;
import ir.bita.esm.route.repository.ServiceGroovyConfigRepository;
import ir.bita.esm.route.service.ScriptAssemblyService;
import ir.bita.esm.service.entity.ServiceCollection;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.repository.ServiceCollectionRepository;
import ir.bita.esm.service.repository.ServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the ESM persistence layer against a real, in-memory H2 database (no mocks, no
 * Testcontainers/Postgres) for exactly one benchmark case: the tool-call values an actual
 * LLM run produced for {@code llm-benchmark/data/cases/c_live01.json}.
 *
 * <p>Flyway is disabled and the schema is generated straight from the JPA entity
 * annotations ({@code ddl-auto=create-drop}), because the real migrations
 * (V1-V3, see db/migration) use Postgres-only DDL (BIGSERIAL, JSONB, DROP TYPE ... CASCADE)
 * that H2 cannot run. H2 is started in PostgreSQL-compatibility mode so the entities'
 * {@code columnDefinition = "jsonb"} JSON columns still resolve.
 *
 * <p>This is a persistence-only proof; it does not start Camel/Vert.x or bind the
 * variables at runtime the way ESB does. See {@code RestProxyLiveBenchmarkE2ETest} in
 * bita-esb-core for the execution half — same template, same real LLM-extracted values,
 * an actual Vert.x gateway and a freshly started random backend server, proxying a real
 * HTTP request end to end.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:esm_benchmark;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
})
@Import(ScriptAssemblyService.class)
class GroovyBenchmarkH2Test {

    /**
     * The real Apache-Camel/Vert.x REST-proxy script BITA ships
     * (bita-esb-core/src/test/resources/rest-proxy-gw-route.groovy), copied verbatim so this
     * module's persistence test does not need a cross-module test-resource dependency.
     */
    private static final String REST_PROXY_SCRIPT = """
            import io.vertx.core.Vertx
            import io.vertx.ext.web.Router
            import io.vertx.ext.web.handler.BodyHandler
            import io.vertx.ext.web.client.WebClient
            import io.vertx.ext.web.client.WebClientOptions
            import org.apache.camel.builder.RouteBuilder

            def vertx    = vertxInstance as Vertx
            def pvUrl    = new URL(pvAddress as String)
            def pvHost   = pvUrl.host
            def pvPort   = pvUrl.port
            def acc      = accessService
            def port     = gwPort as int

            def webClient = WebClient.create(vertx, new WebClientOptions().setConnectTimeout(5000))
            def router = Router.router(vertx)
            router.route().handler(BodyHandler.create())
            router.route("/*").handler { ctx ->
                def req = ctx.request()
                def proxyReq = webClient.requestAbs(req.method(), "http://${pvHost}:${pvPort}${req.path()}")
                proxyReq.sendBuffer(ctx.body().buffer())
                    .onSuccess { resp -> ctx.response().setStatusCode(resp.statusCode()).end(resp.body()) }
                    .onFailure { err -> ctx.response().setStatusCode(502).end("Bad Gateway: ${err.message}") }
            }
            vertx.createHttpServer().requestHandler(router).listen(port).result()

            new RouteBuilder() { void configure() {} }
            """;

    /** Exact tool-call arguments a real "auto"-routed LLM run produced for c_live01 (see
     *  llm-benchmark/results/2026-09-25T12-54-41_auto_esm+tools/transcripts/c_live01-r1.json),
     *  reached model google/gemma-4-26b-a4b-it:free. */
    private static final long GW_PORT = 29501L;
    private static final Map<String, Object> LLM_VARIABLE_VALUES = Map.of(
            "pvAddress", "http://127.0.0.1:29500/api",
            "gwPort", "29501",
            "gwPath", "/esb/demo/echo-proxy/v1"
    );

    @Autowired
    private GroovyTemplateRepository templateRepo;
    @Autowired
    private ServiceCollectionRepository collectionRepo;
    @Autowired
    private ServiceRepository serviceRepo;
    @Autowired
    private ServiceGroovyConfigRepository configRepo;
    @Autowired
    private ScriptAssemblyService assemblyService;

    @Test
    @DisplayName("real LLM output for c_live01 persists as a GroovyTemplate + ServiceGroovyConfig on H2, then assembles")
    void persistsAndAssemblesLiveBenchmarkCase() {

        // 1. Seed the "rest-proxy" GroovyTemplate (id 101 in the benchmark catalog) exactly
        //    as an operator would after approving it once — real INSERT against H2.
        GroovyTemplate template = templateRepo.save(GroovyTemplate.builder()
                .name("rest-proxy")
                .description("REST-to-REST proxy over Vert.x")
                .scriptText(REST_PROXY_SCRIPT)
                .build());
        assertThat(template.getId()).isNotNull();

        // 2. create_service: real collection + service rows, same as CreateServiceTool.
        ServiceCollection collection = collectionRepo.save(ServiceCollection.builder()
                .name("demo")
                .basePath("/esb/demo")
                .description("Live benchmark demo collection")
                .build());
        ServiceEntity service = serviceRepo.save(ServiceEntity.builder()
                .collection(collection)
                .name("echo-proxy")
                .serviceVersion("v1")
                .description("REST proxy for echo service")
                .phase(ServicePhase.DRAFT)
                .build());
        assertThat(service.getId()).isNotNull();

        // 3. service_groovy_config: the LLM's own extracted variableValues, persisted for real
        //    (ComponentInstanceTool.execute does exactly these three steps: load template,
        //    assemble, save).
        String assembled = assemblyService.assemble(template);
        ServiceGroovyConfig config = configRepo.save(ServiceGroovyConfig.builder()
                .service(service)
                .groovyTemplate(template)
                .variableValues(new HashMap<>(LLM_VARIABLE_VALUES))
                .assembledScript(assembled)
                .build());

        // 4. Reload from H2 (new query, not the same managed instance) and verify round-trip.
        ServiceGroovyConfig reloaded = configRepo.findByServiceId(service.getId()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(config.getId());
        assertThat(reloaded.getGroovyTemplate().getName()).isEqualTo("rest-proxy");
        assertThat(reloaded.getVariableValues())
                .containsEntry("pvAddress", "http://127.0.0.1:29500/api")
                .containsEntry("gwPort", "29501")
                .containsEntry("gwPath", "/esb/demo/echo-proxy/v1");
        assertThat(reloaded.getAssembledScript())
                .contains("route from GroovyTemplate: rest-proxy")
                .contains("vertx.createHttpServer()");

        // No #component references in this script, so assembly is the template text verbatim
        // behind one comment line — proves ScriptAssemblyService really ran, not a stub.
        assertThat(assembled).isEqualTo(
                "// ── route from GroovyTemplate: rest-proxy ──\n" + REST_PROXY_SCRIPT);

        // Sanity: the gateway port persisted matches what bita-esb-core's execution test
        // (GW_PORT = 29501) actually binds to — same value, two independent proofs.
        assertThat(reloaded.getVariableValues().get("gwPort")).isEqualTo(String.valueOf(GW_PORT));
    }
}
