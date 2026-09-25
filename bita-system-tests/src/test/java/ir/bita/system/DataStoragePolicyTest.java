package ir.bita.system;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import ir.bita.common.domain.VariableType;
import ir.bita.esm.llm.tool.ToolRegistry;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.repository.ComponentTemplateRepository;
import ir.bita.esm.route.repository.ServiceGroovyConfigRepository;
import ir.bita.esm.route.service.ServiceConfigVersionService;
import ir.bita.system.support.SoapStubProvider;
import ir.bita.system.support.SystemTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security — the "nothing is stored" policy, enforced by the tool layer.
 *
 * <p>BITA only passes data through. Whatever a model proposes and a human confirms still goes
 * through ESM's tools, and they scan what would actually run — the configured values and the
 * template's or component's Groovy code — for storage targets (databases, local file writes)
 * before storing anything. Setup: ESM's real tools on H2, a rest-proxy service already running on
 * a real ESB. Action: ask the tools to (a) point the running service at a database, (b) configure a
 * service from a template that writes responses to local files, (c) create a component whose code
 * writes a file, and (d) configure a SOAP service whose WSDL is read from a local file. Expectation:
 * (a)–(c) are refused with the offending text named, nothing of them is stored, and the running
 * service keeps its version and keeps serving; (d) — reading, not storing — is accepted.
 */
@DisplayName("امنیت: سیاست عدم ذخیره‌سازی در لایهٔ ابزار")
class DataStoragePolicyTest extends SystemTestBase {

    @Autowired
    ToolRegistry tools;

    @Autowired
    ServiceGroovyConfigRepository configRepository;

    @Autowired
    ComponentTemplateRepository componentTemplateRepository;

    @Autowired
    ServiceConfigVersionService versionService;

    @Test
    @DisplayName("a database target on a running service is refused; the service keeps its version and serves")
    void databaseTargetIsRefused() throws Exception {
        Backend provider = startBackend("pass-through");
        GroovyTemplate template = seedRestProxyTemplate();
        int gwPort = freePort();
        long serviceId = createService("storage-guarded");
        Map<String, Object> good = Map.of("pvAddress", provider.address(),
                "gwPort", String.valueOf(gwPort), "gwPath", "/esb/storage-guarded/v1");
        executeTool("service_groovy_config", Map.of("serviceId", serviceId,
                "groovyTemplateId", template.getId(), "variableValues", new HashMap<>(good)));
        String apiKey = grantApiKeyConsumer(serviceId, "storage-consumer");
        startEsb(serviceId);
        String versionBefore = versionService.activeLabel(serviceId).orElseThrow();

        Map<String, Object> toDatabase = new HashMap<>(good);
        toDatabase.put("pvAddress", "jdbc:mysql://10.0.0.5:3306/archive");
        Map<String, Object> result = tools.executeTool("service_groovy_config", Map.of("serviceId", serviceId,
                "groovyTemplateId", template.getId(), "variableValues", toDatabase));

        assertRefused(result, "jdbc:");
        assertThat(String.valueOf(configRepository.findByServiceId(serviceId).orElseThrow()
                .getVariableValues().get("pvAddress"))).isEqualTo(provider.address());
        assertThat(versionService.activeLabel(serviceId)).contains(versionBefore);
        assertThat(versionService.versions(serviceId)).hasSize(1);

        Vertx v = Vertx.vertx();
        try {
            HttpResponse<Buffer> resp = WebClient.create(v).get(gwPort, "127.0.0.1", "/ping")
                    .putHeader("X-API-Key", apiKey).send()
                    .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.bodyAsJsonObject().getString("marker")).isEqualTo("pass-through");
        } finally {
            v.close();
        }
    }

    @Test
    @DisplayName("a template whose code writes responses to local files cannot configure a service")
    void fileWritingTemplateIsRefused() throws Exception {
        GroovyTemplate archiving = seedTemplate("archive-to-file", "REST proxy that archives responses", List.of(
                variable("pvAddress", "آدرس سرویس ارائه‌دهنده", VariableType.STRING),
                variable("gwPort", "پورت گیت‌وی", VariableType.PORT),
                variable("gwPath", "مسیر Ingress", VariableType.STRING),
                variable("archiveDir", "پوشهٔ بایگانی", VariableType.STRING)));
        long serviceId = createService("archiving");

        Map<String, Object> result = tools.executeTool("service_groovy_config", Map.of("serviceId", serviceId,
                "groovyTemplateId", archiving.getId(), "variableValues", new HashMap<>(Map.of(
                        "pvAddress", "http://127.0.0.1:" + freePort() + "/api",
                        "gwPort", String.valueOf(freePort()), "gwPath", "/esb/archiving/v1",
                        "archiveDir", "/var/bita/archive"))));

        assertRefused(result, "FileOutputStream");
        assertThat(configRepository.findByServiceId(serviceId)).isEmpty();
        assertThat(versionService.versions(serviceId)).isEmpty();
    }

    @Test
    @DisplayName("a component whose code writes a file is not created")
    void fileWritingComponentIsRefused() {
        String name = "response-archiver-" + System.nanoTime();
        Map<String, Object> result = tools.executeTool("component_template", Map.of(
                "name", name,
                "description", "keeps a copy of each message",
                "componentType", "PROCESSOR",
                "className", "ir.bita.components.ResponseArchiver",
                "groovyCode", """
                        class ResponseArchiver implements org.apache.camel.Processor {
                            void process(org.apache.camel.Exchange ex) {
                                java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/archive.txt"),
                                        ex.message.getBody(String))
                            }
                        }"""));

        assertRefused(result, "Files.write");
        assertThat(componentTemplateRepository.findByNameAndDeletedFalse(name)).isEmpty();
    }

    @Test
    @DisplayName("reading a WSDL from a local file is not storage and is accepted")
    void readingALocalWsdlIsAccepted() throws Exception {
        try (SoapStubProvider provider = new SoapStubProvider(freePort(), "file-wsdl", null, null)) {
            Path wsdl = Files.createTempFile("provider-", ".wsdl");
            Vertx v = Vertx.vertx();
            try {
                String body = WebClient.create(v).getAbs(provider.wsdlUri()).send()
                        .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS).bodyAsString();
                Files.writeString(wsdl, body);
            } finally {
                v.close();
            }
            GroovyTemplate t = seedTemplate("soap-passthrough", "SOAP passthrough, no WS-Security", List.of(
                    variable("pvAddress", "آدرس سرویس SOAP", VariableType.STRING),
                    variable("pvWsdlUri", "آدرس WSDL", VariableType.STRING),
                    variable("gwPort", "پورت گیت‌وی", VariableType.PORT),
                    variable("gwPath", "مسیر Ingress", VariableType.STRING)));
            long serviceId = createService("file-wsdl");

            Map<String, Object> result = tools.executeTool("service_groovy_config", Map.of("serviceId", serviceId,
                    "groovyTemplateId", t.getId(), "variableValues", new HashMap<>(Map.of(
                            "pvAddress", provider.endpoint(), "pvWsdlUri", wsdl.toUri().toString(),
                            "gwPort", String.valueOf(freePort()), "gwPath", "/esb/file-wsdl/v1"))));

            assertThat(result.get("error")).as("result: %s", result).isNotEqualTo(true);
            assertThat(configRepository.findByServiceId(serviceId)).isPresent();
        }
    }

    private long createService(String name) {
        Map<String, Object> created = executeTool("create_service", Map.of(
                "serviceName", name, "serviceVersion", "v1", "collectionName", name));
        return ((Number) created.get("serviceId")).longValue();
    }

    private static void assertRefused(Map<String, Object> result, String offending) {
        assertThat(result.get("error")).as("result: %s", result).isEqualTo(true);
        assertThat(String.valueOf(result.get("message"))).contains(offending);
        System.out.printf("[STORAGE-POLICY] refused: %s%n", result.get("message"));
    }
}
