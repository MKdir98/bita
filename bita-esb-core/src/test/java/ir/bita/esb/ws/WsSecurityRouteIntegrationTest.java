package ir.bita.esb.ws;

import ir.bita.esb.route.DynamicRouteBuilder;
import ir.bita.esb.route.RouteDefinition;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for WS-Security route using RouteDefinition and DynamicRouteBuilder.
 * Client (X.509) -> ESB route (decrypt/verify) -> Mock backend -> Response (encrypt).
 * All config comes from RouteDefinition.inputConfig (wsSecurity) - storable in DB.
 */
class WsSecurityRouteIntegrationTest {

    private static final int ESB_PORT = 19283;
    private static final String ESB_ADDRESS = "http://localhost:" + ESB_PORT + "/ping";

    @TempDir
    Path tempDir;

    private CamelContext camelContext;
    private TestKeyStoreGenerator.TestKeyStores keyStores;

    @BeforeEach
    void setUp() throws Exception {
        keyStores = TestKeyStoreGenerator.generate(tempDir);

        Path wsdlPath = Path.of(getClass().getResource("/ws-security/ping.wsdl").toURI());
        String wsdlUrl = "file:" + wsdlPath.toAbsolutePath();

        camelContext = new DefaultCamelContext();

        // Route via RouteDefinition - inputConfig with wsSecurity (all storable in DB)
        Map<String, Object> wsSecurity = Map.of(
                "bitaKeystorePropsPath", keyStores.bitaKeystorePropsPath(),
                "clientTruststorePropsPath", keyStores.clientTruststorePropsPath(),
                "password", TestKeyStoreGenerator.PASSWORD);

        Map<String, Object> inputConfig = Map.of(
                "address", ESB_ADDRESS,
                "wsdlUrl", wsdlUrl,
                "serviceClass", PingServiceImpl.class.getCanonicalName(),
                "dataFormat", "PAYLOAD",
                "wsSecurity", wsSecurity);

        RouteDefinition routeDef = RouteDefinition.builder()
                .routeId(1L)
                .name("ws-security-ping")
                .active(true)
                .inputEndpointType("CXF")
                .inputConfig(inputConfig)
                .outputUri("direct:mockBackend")
                .components(java.util.List.of())
                .build();

        DynamicRouteBuilder dynamicRouteBuilder = new DynamicRouteBuilder(camelContext);
        camelContext.addRoutes(dynamicRouteBuilder.buildRoute(routeDef, "ws-security-ping"));

        // Mock backend (destination service) - inline in test
        camelContext.addRoutes(new org.apache.camel.builder.RouteBuilder() {
            @Override
            public void configure() {
                from("direct:mockBackend")
                        .routeId("mock-backend")
                        .process(exchange -> {
                            exchange.getMessage().setBody("""
                                    <?xml version="1.0" encoding="UTF-8"?>
                                    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                                                   xmlns:ns="http://ws.esb.bita.ir/">
                                        <soap:Body>
                                            <ns:pingResponse>pong: test-message</ns:pingResponse>
                                        </soap:Body>
                                    </soap:Envelope>
                                    """);
                            exchange.getMessage().setHeader(org.apache.camel.Exchange.CONTENT_TYPE, "application/soap+xml");
                        });
            }
        });

        camelContext.start();
        Thread.sleep(2000);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void shouldReceiveEncryptedResponseWhenClientCallsWithValidCertificate() throws Exception {
        SoapTestClient client = new SoapTestClient(
                ESB_ADDRESS,
                keyStores.clientJks().toString(),
                keyStores.bitaTruststorePropsPath(),
                TestKeyStoreGenerator.PASSWORD,
                TestKeyStoreGenerator.CLIENT_ALIAS);

        String response = client.callPing("test-message");
        assertThat(response).contains("pong");
    }

    private static class SoapTestClient {
        private final String endpointUrl;
        private final String clientKeystorePath;
        private final String bitaTruststorePropsPath;
        private final String keystorePassword;
        private final String alias;

        SoapTestClient(String endpointUrl, String clientKeystorePath, String bitaTruststorePropsPath,
                       String keystorePassword, String alias) {
            this.endpointUrl = endpointUrl;
            this.clientKeystorePath = clientKeystorePath;
            this.bitaTruststorePropsPath = bitaTruststorePropsPath;
            this.keystorePassword = keystorePassword;
            this.alias = alias;
        }

        String callPing(String message) throws Exception {
            javax.xml.namespace.QName serviceName = new javax.xml.namespace.QName("http://ws.esb.bita.ir/", "PingService");
            javax.xml.namespace.QName portName = new javax.xml.namespace.QName("http://ws.esb.bita.ir/", "PingServicePort");

            URL wsdlUrl = new URL(endpointUrl + "?wsdl");
            jakarta.xml.ws.Service service = jakarta.xml.ws.Service.create(wsdlUrl, serviceName);
            PingService port = service.getPort(portName, PingService.class);

            jakarta.xml.ws.BindingProvider bp = (jakarta.xml.ws.BindingProvider) port;
            bp.getRequestContext().put(jakarta.xml.ws.BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpointUrl);
            bp.getRequestContext().put("ws-security.signature.properties", createClientSigProps());
            bp.getRequestContext().put("ws-security.encryption.properties", bitaTruststorePropsPath);
            bp.getRequestContext().put("ws-security.encryption.username", TestKeyStoreGenerator.BITA_ALIAS);
            bp.getRequestContext().put("ws-security.callback-handler", new CallbackHandler() {
                @Override
                public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                    for (Callback cb : callbacks) {
                        if (cb instanceof org.apache.wss4j.common.ext.WSPasswordCallback pc) {
                            pc.setPassword(keystorePassword);
                        }
                    }
                }
            });

            return port.ping(message);
        }

        private String createClientSigProps() throws IOException {
            Path props = Files.createTempFile("client-sig", ".properties");
            String content = """
                    org.apache.ws.security.crypto.provider=org.apache.ws.security.components.crypto.Merlin
                    org.apache.ws.security.crypto.merlin.keystore.type=jks
                    org.apache.ws.security.crypto.merlin.keystore.password=%s
                    org.apache.ws.security.crypto.merlin.keystore.private.password=%s
                    org.apache.ws.security.crypto.merlin.keystore.file=%s
                    org.apache.ws.security.crypto.merlin.keystore.alias=%s
                    """.formatted(keystorePassword, keystorePassword, clientKeystorePath.replace("\\", "/"), alias);
            Files.writeString(props, content, StandardCharsets.UTF_8);
            return props.toAbsolutePath().toString();
        }
    }
}
