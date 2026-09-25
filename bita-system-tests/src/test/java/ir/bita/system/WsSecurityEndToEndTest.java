package ir.bita.system;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import ir.bita.system.support.BenchmarkBase;
import ir.bita.system.support.ws.EchoServiceImpl;
import ir.bita.system.support.ws.TestKeyStoreGenerator;
import ir.bita.system.support.ws.WsSecurityParties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1 — research question 4 (WS-Security preserved): does a SOAP service built from the
 * soap-ws-security GroovyTemplate, with the LLM supplying its parameters, keep WS-Security 1.1
 * intact end to end — and refuse what it must refuse?
 *
 * <p>Setup: the service for benchmark document c04 (SOAP, WS-Security) on a real ESB holding
 * the gateway's keystore; the provider is a CXF service enforcing the document's policy; a
 * consumer organisation is registered in ESM with its X.509 certificate and granted access.
 * Expectations: the granted consumer's signed and encrypted call is answered correctly and its
 * reply verifies; an unsigned call is refused and never reaches the provider; a consumer whose
 * certificate ESM never granted is refused and never reaches the provider.
 */
@DisplayName("P1 [سؤال ۴] حفظ سرتاسری WS-Security 1.1 در سرویس ساخته‌شده از قالب")
class WsSecurityEndToEndTest extends BenchmarkBase {

    @TempDir
    Path strangerKeys;

    @Test
    void wsSecurityHoldsEndToEnd() throws Exception {
        DefinedService service = defineService("c04", freePort(), "unused");
        grantConsumer(service.serviceId(), "granted-org", wssParties.consumerCertificatePem());
        startEsb(service.serviceId(), wssParties.keyStores().gwKeystorePropsPath(), TestKeyStoreGenerator.PASSWORD);
        String gateway = "http://127.0.0.1:" + service.gwPort() + service.gwPath();
        Map<String, Object> results = new LinkedHashMap<>();

        EchoServiceImpl.CALLS.set(0);
        String answer = wssParties.consumer(gateway).echo("signed-call", 0);
        results.put("grantedConsumerAnswer", answer);
        assertThat(answer).isEqualTo("I getsigned-call");
        assertThat(EchoServiceImpl.CALLS.get()).isEqualTo(1);

        Vertx vertx = Vertx.vertx();
        try {
            HttpResponse<Buffer> unsigned = WebClient.create(vertx).postAbs(gateway)
                    .putHeader("Content-Type", "text/xml; charset=utf-8")
                    .sendBuffer(Buffer.buffer("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                            + "<soapenv:Body><ns:echo xmlns:ns=\"http://ws.esb.bita.ir/\"><message>unsigned</message><time>0</time>"
                            + "</ns:echo></soapenv:Body></soapenv:Envelope>"))
                    .toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
            results.put("unsignedStatus", unsigned.statusCode());
            results.put("unsignedBody", unsigned.bodyAsString());
            results.put("providerCallsAfterUnsigned", EchoServiceImpl.CALLS.get());
            assertThat(unsigned.statusCode()).as("unsigned call; provider calls=%d; body=%s",
                    EchoServiceImpl.CALLS.get(), unsigned.bodyAsString()).isEqualTo(500);
            assertThat(unsigned.bodyAsString()).contains("Fault");
        } finally {
            vertx.close();
        }
        assertThat(EchoServiceImpl.CALLS.get()).as("unsigned call must not reach the provider").isEqualTo(1);

        WsSecurityParties stranger = new WsSecurityParties(strangerKeys);
        assertThatThrownBy(() -> stranger.consumer(gateway).echo("stranger", 0))
                .as("consumer never granted in ESM");
        results.put("strangerRefused", true);
        assertThat(EchoServiceImpl.CALLS.get()).as("ungranted consumer must not reach the provider").isEqualTo(1);

        results.put("llmSource", service.source());
        results.put("providerCalls", EchoServiceImpl.CALLS.get());
        report("P1", service, results);
    }
}
