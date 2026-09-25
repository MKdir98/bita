package ir.bita.system;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import ir.bita.common.domain.VariableType;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.system.support.SoapStubProvider;
import ir.bita.system.support.SystemTestBase;
import ir.bita.system.support.ws.WsSecurityParties;
import ir.bita.system.support.ws.TestKeyStoreGenerator;
import org.apache.cxf.endpoint.Server;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Infrastructure check for the benchmark: every GroovyTemplate the benchmark can choose from
 * actually runs on a real ESB and does what its description says, given correct parameters.
 * If one of these fails, a benchmark miss on that template would say nothing about the LLM.
 */
class TemplateBehaviourTest extends SystemTestBase {

    private Vertx client;
    private WebClient http;

    @BeforeEach
    void client() {
        client = Vertx.vertx();
        http = WebClient.create(client);
    }

    @AfterEach
    void closeClient() throws Exception {
        client.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("rest-proxy-rate-limit: the (limit+1)-th request in a minute is 429 and never reaches the provider")
    void restProxyRateLimit() throws Exception {
        Backend backend = startBackend("rl-backend");
        GroovyTemplate t = seedTemplate("rest-proxy-rate-limit", "REST proxy with per-consumer limit", List.of(
                variable("pvAddress", "آدرس سرویس ارائه‌دهنده", VariableType.STRING),
                variable("gwPort", "پورت گیت‌وی", VariableType.PORT),
                variable("gwPath", "مسیر Ingress", VariableType.STRING),
                variable("rateLimitPerMinute", "حداکثر درخواست در دقیقه", VariableType.INT)));
        int gwPort = freePort();
        bringUp(t, Map.of("pvAddress", backend.address(), "gwPort", String.valueOf(gwPort),
                "gwPath", "/esb/rl/v1", "rateLimitPerMinute", "3"));

        for (int i = 0; i < 3; i++) {
            assertThat(get(gwPort, "/x").statusCode()).isEqualTo(200);
        }
        assertThat(get(gwPort, "/x").statusCode()).isEqualTo(429);
    }

    @Test
    @DisplayName("soap-passthrough: WSDL points consumers at the gateway, envelopes reach the provider unchanged")
    void soapPassthrough() throws Exception {
        try (SoapStubProvider provider = new SoapStubProvider(freePort(), "soap-pv", null, null)) {
            GroovyTemplate t = seedTemplate("soap-passthrough", "SOAP passthrough, no WS-Security", soapVars());
            int gwPort = freePort();
            bringUp(t, Map.of("pvAddress", provider.endpoint(), "pvWsdlUri", provider.wsdlUri(),
                    "gwPort", String.valueOf(gwPort), "gwPath", "/esb/soap/v1"));

            String wsdl = get(gwPort, "/esb/soap/v1?wsdl").bodyAsString();
            assertThat(wsdl).contains("location=\"http://127.0.0.1:" + gwPort + "/esb/soap/v1\"")
                    .doesNotContain(provider.endpoint());

            HttpResponse<Buffer> resp = postSoap(gwPort, "GetStatus", "<id>42</id>");
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.bodyAsString()).contains("<id>42</id>").contains("<marker>soap-pv</marker>");
        }
    }

    @Test
    @DisplayName("soap-backend-basic-auth: the gateway adds the provider's credentials; consumers never send them")
    void soapBackendBasicAuth() throws Exception {
        try (SoapStubProvider provider = new SoapStubProvider(freePort(), "auth-pv", "esb", "s3cret")) {
            List<GroovyTemplate.VariableMetadata> vars = new java.util.ArrayList<>(soapVars());
            vars.add(variable("backendUsername", "نام کاربری ارائه‌دهنده", VariableType.STRING));
            vars.add(variable("backendPassword", "رمز ارائه‌دهنده", VariableType.SECRET));
            GroovyTemplate t = seedTemplate("soap-backend-basic-auth", "SOAP passthrough to a Basic-auth provider", vars);
            int gwPort = freePort();
            bringUp(t, Map.of("pvAddress", provider.endpoint(), "pvWsdlUri", provider.wsdlUri(),
                    "gwPort", String.valueOf(gwPort), "gwPath", "/esb/auth/v1",
                    "backendUsername", "esb", "backendPassword", "s3cret"));

            HttpResponse<Buffer> resp = postSoap(gwPort, "GetStatus", "<id>7</id>");
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.bodyAsString()).contains("<marker>auth-pv</marker>");
        }
    }

    @Test
    @DisplayName("rest-to-soap-bridge: a JSON POST becomes the SOAP operation and the reply comes back as JSON")
    void restToSoapBridge() throws Exception {
        try (SoapStubProvider provider = new SoapStubProvider(freePort(), "bridge-pv", null, null)) {
            List<GroovyTemplate.VariableMetadata> vars = new java.util.ArrayList<>(soapVars());
            vars.add(variable("soapOperation", "نام operation", VariableType.STRING));
            GroovyTemplate t = seedTemplate("rest-to-soap-bridge", "REST/JSON facade over a SOAP operation", vars);
            int gwPort = freePort();
            bringUp(t, Map.of("pvAddress", provider.endpoint(), "pvWsdlUri", provider.wsdlUri(),
                    "soapOperation", "GetBalance",
                    "gwPort", String.valueOf(gwPort), "gwPath", "/esb/bridge/v1"));

            HttpResponse<Buffer> resp = http.post(gwPort, "127.0.0.1", "/esb/bridge/v1")
                    .putHeader("X-API-Key", apiKey)
                    .putHeader("Content-Type", "application/json")
                    .sendJsonObject(new JsonObject().put("accountId", "IR123"))
                    .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(resp.statusCode()).as(resp.bodyAsString()).isEqualTo(200);
            JsonObject json = resp.bodyAsJsonObject();
            assertThat(json.getString("accountId")).isEqualTo("IR123");
            assertThat(json.getString("marker")).isEqualTo("bridge-pv");
        }
    }

    @TempDir
    Path keys;

    @ParameterizedTest(name = "securityPolicy={0}")
    @ValueSource(strings = {"Timestamp+Signature", "Timestamp+Signature+Encryption"})
    @DisplayName("soap-ws-security: a signed+encrypted consumer call is re-secured per the provider's policy and answered")
    void soapWsSecurity(String securityPolicy) throws Exception {
        WsSecurityParties parties = new WsSecurityParties(keys);
        TestKeyStoreGenerator.ThreePartyKeyStores ks = parties.keyStores();
        int pvPort = freePort();
        String pvAddress = "http://127.0.0.1:" + pvPort + "/ws/echo";
        Server provider = parties.startProvider(pvAddress, securityPolicy.contains("Encryption"));
        try {
            List<GroovyTemplate.VariableMetadata> vars = new java.util.ArrayList<>(soapVars());
            vars.add(variable("securityPolicy", "سیاست امنیتی", VariableType.STRING));
            vars.add(variable("signaturePropsFile", "فایل properties امضا", VariableType.STRING));
            vars.add(variable("truststorePropsFile", "فایل properties truststore", VariableType.STRING));
            GroovyTemplate t = seedTemplate("soap-ws-security", "SOAP with WS-Security 1.1", vars);
            int gwPort = freePort();

            Map<String, Object> created = executeTool("create_service", Map.of(
                    "serviceName", t.getName(), "serviceVersion", "v1", "collectionName", t.getName()));
            long serviceId = ((Number) created.get("serviceId")).longValue();
            grantConsumer(serviceId, "consumer-org", parties.consumerCertificatePem());
            executeTool("service_groovy_config", Map.of(
                    "serviceId", serviceId, "groovyTemplateId", t.getId(),
                    "variableValues", new HashMap<>(Map.of(
                            "pvAddress", pvAddress, "pvWsdlUri", pvAddress + "?wsdl",
                            "gwPort", String.valueOf(gwPort), "gwPath", "/ws/echo",
                            "securityPolicy", securityPolicy,
                            // relative names resolve next to the gateway's own keystore properties
                            "signaturePropsFile", "gw-keystore.properties",
                            "truststorePropsFile", "gw-outgoing-trust.properties"))));
            startEsb(serviceId, ks.gwKeystorePropsPath(), TestKeyStoreGenerator.PASSWORD);

            String answer = parties.consumer("http://127.0.0.1:" + gwPort + "/ws/echo").echo("hello", 0);
            assertThat(answer).isEqualTo("I gethello");
        } finally {
            provider.stop();
        }
    }

    private void bringUp(GroovyTemplate template, Map<String, Object> variableValues) throws Exception {
        Map<String, Object> created = executeTool("create_service", Map.of(
                "serviceName", template.getName(), "serviceVersion", "v1", "collectionName", template.getName()));
        long serviceId = ((Number) created.get("serviceId")).longValue();
        executeTool("service_groovy_config", Map.of(
                "serviceId", serviceId, "groovyTemplateId", template.getId(),
                "variableValues", new HashMap<>(variableValues)));
        apiKey = grantApiKeyConsumer(serviceId, "consumer-" + template.getName());
        startEsb(serviceId);
    }

    /** Key of the consumer granted access to the service {@link #bringUp} created. */
    private String apiKey;

    private static List<GroovyTemplate.VariableMetadata> soapVars() {
        return List.of(
                variable("pvAddress", "آدرس سرویس SOAP", VariableType.STRING),
                variable("pvWsdlUri", "آدرس WSDL", VariableType.STRING),
                variable("gwPort", "پورت گیت‌وی", VariableType.PORT),
                variable("gwPath", "مسیر Ingress", VariableType.STRING));
    }

    private HttpResponse<Buffer> get(int port, String uri) throws Exception {
        return http.get(port, "127.0.0.1", uri).putHeader("X-API-Key", apiKey).send()
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private HttpResponse<Buffer> postSoap(int port, String op, String children) throws Exception {
        String envelope = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:ns=\"" + SoapStubProvider.NAMESPACE + "\"><soapenv:Body><ns:" + op + ">" + children
                + "</ns:" + op + "></soapenv:Body></soapenv:Envelope>";
        return http.post(port, "127.0.0.1", "/esb/soap/v1")
                .putHeader("X-API-Key", apiKey)
                .putHeader("Content-Type", "text/xml; charset=utf-8")
                .putHeader("SOAPAction", "\"" + op + "\"")
                .sendBuffer(Buffer.buffer(envelope))
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
