package ir.bita.esb.cxf;

import org.apache.camel.component.cxf.jaxws.CxfConfigurer;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T2: unit tests for {@link WsSecurityCxfConfigurerFactory} — the CxfConfigurer factory
 * that wires WS-Security interceptors onto Camel CXF endpoints, replacing the coverage the
 * thesis originally described against the (now deleted) DynamicRouteBuilder. This factory
 * is the piece of the current Groovy-script route architecture that plays the same role:
 * see {@code ws-security/echo-gw-route.groovy}'s {@code cxfConfigurer=#...} bindings.
 */
@DisplayName("WsSecurityCxfConfigurerFactory")
class WsSecurityCxfConfigurerFactoryTest {

    @Test
    @DisplayName("T2-1: outbound (SOAP-client) configurer wires WS-Security interceptors onto the CXF Client")
    void outboundConfigurerWiresClientInterceptors() {
        CxfConfigurer configurer = WsSecurityCxfConfigurerFactory.createSoapClientConfigurer(validSoapClientConfig());

        MockClient client = new MockClient();
        configurer.configureClient(client.mock);

        assertThat(client.outInterceptors).anyMatch(i -> i instanceof WSS4JOutInterceptor);
        assertThat(client.inInterceptors).anyMatch(i -> i instanceof WSS4JInInterceptor);
        assertThat(client.outFaultInterceptors).anyMatch(i -> i instanceof WSS4JOutInterceptor);
        assertThat(client.inFaultInterceptors).anyMatch(i -> i instanceof WSS4JInInterceptor);
    }

    @Test
    @DisplayName("T2-2: outbound (SOAP-client) configurer's configureServer is a no-op")
    void outboundConfigurerConfigureServerIsNoOp() {
        CxfConfigurer configurer = WsSecurityCxfConfigurerFactory.createSoapClientConfigurer(validSoapClientConfig());

        Server server = mock(Server.class);
        configurer.configureServer(server);

        verifyNoInteractions(server);
    }

    @Test
    @DisplayName("T2-3: template reuse — building the same outbound config twice for two different services succeeds independently")
    void templateReusedAcrossTwoServicesDoesNotConflict() {
        CxfConfigurer serviceA = WsSecurityCxfConfigurerFactory.createSoapClientConfigurer(validSoapClientConfig());
        CxfConfigurer serviceB = WsSecurityCxfConfigurerFactory.createSoapClientConfigurer(validSoapClientConfig());

        MockClient clientA = new MockClient();
        MockClient clientB = new MockClient();
        serviceA.configureClient(clientA.mock);
        serviceB.configureClient(clientB.mock);

        assertThat(clientA.outInterceptors).anyMatch(i -> i instanceof WSS4JOutInterceptor);
        assertThat(clientB.outInterceptors).anyMatch(i -> i instanceof WSS4JOutInterceptor);
    }

    @Test
    @DisplayName("T2-4: inbound (server-side) configurer wires WS-Security interceptors onto the CXF Endpoint, configureClient is a no-op")
    void inboundConfigurerWiresServerInterceptorsAndLeavesClientAlone() {
        CxfConfigurer configurer = WsSecurityCxfConfigurerFactory.create(
                "/path/to/bita-keystore.properties", "/path/to/client-truststore.properties", "secret");

        MockEndpoint endpoint = new MockEndpoint();
        Server server = mock(Server.class);
        when(server.getEndpoint()).thenReturn(endpoint.mock);

        configurer.configureServer(server);

        assertThat(endpoint.outInterceptors).anyMatch(i -> i instanceof WSS4JOutInterceptor);
        assertThat(endpoint.inInterceptors).anyMatch(i -> i instanceof WSS4JInInterceptor);

        Client client = mock(Client.class);
        configurer.configureClient(client);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("T2-5: missing soapClientSignaturePropsFile fails fast with a clear IllegalArgumentException")
    void missingSoapClientSignaturePropsFileFailsFast() {
        Map<String, Object> config = validSoapClientConfig();
        config.remove(WsSecurityCxfConfigurerFactory.SOAP_CLIENT_SIGNATURE_PROPS_FILE);

        assertThatThrownBy(() -> WsSecurityCxfConfigurerFactory.createSoapClientConfigurer(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(WsSecurityCxfConfigurerFactory.SOAP_CLIENT_SIGNATURE_PROPS_FILE);
    }

    @Test
    @DisplayName("T2-6: missing bitaTruststorePropsPath fails fast with a clear IllegalArgumentException")
    void missingBitaTruststorePropsPathFailsFast() {
        Map<String, Object> config = validSoapClientConfig();
        config.remove(WsSecurityCxfConfigurerFactory.BITA_TRUSTSTORE_PROPS_PATH);

        assertThatThrownBy(() -> WsSecurityCxfConfigurerFactory.createSoapClientConfigurer(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(WsSecurityCxfConfigurerFactory.BITA_TRUSTSTORE_PROPS_PATH);
    }

    private static Map<String, Object> validSoapClientConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(WsSecurityCxfConfigurerFactory.SOAP_CLIENT_SIGNATURE_PROPS_FILE, "/path/to/gateway-sign.properties");
        config.put(WsSecurityCxfConfigurerFactory.BITA_TRUSTSTORE_PROPS_PATH, "/path/to/org-truststore.properties");
        config.put(WsSecurityCxfConfigurerFactory.PASSWORD, "secret");
        return config;
    }

    /**
     * Mockito's default answer hands back a fresh empty list on every unstubbed call, so
     * {@code client.getOutInterceptors().add(...)} inside the factory would silently mutate
     * a throwaway list. Stub each getter to always return the same backing list instead.
     */
    private static final class MockClient {
        final Client mock = mock(Client.class);
        final List<Interceptor<? extends Message>> outInterceptors = new ArrayList<>();
        final List<Interceptor<? extends Message>> inInterceptors = new ArrayList<>();
        final List<Interceptor<? extends Message>> outFaultInterceptors = new ArrayList<>();
        final List<Interceptor<? extends Message>> inFaultInterceptors = new ArrayList<>();

        MockClient() {
            when(mock.getOutInterceptors()).thenReturn(outInterceptors);
            when(mock.getInInterceptors()).thenReturn(inInterceptors);
            when(mock.getOutFaultInterceptors()).thenReturn(outFaultInterceptors);
            when(mock.getInFaultInterceptors()).thenReturn(inFaultInterceptors);
        }
    }

    private static final class MockEndpoint {
        final Endpoint mock = mock(Endpoint.class);
        final List<Interceptor<? extends Message>> outInterceptors = new ArrayList<>();
        final List<Interceptor<? extends Message>> inInterceptors = new ArrayList<>();
        final List<Interceptor<? extends Message>> outFaultInterceptors = new ArrayList<>();
        final List<Interceptor<? extends Message>> inFaultInterceptors = new ArrayList<>();

        MockEndpoint() {
            when(mock.getOutInterceptors()).thenReturn(outInterceptors);
            when(mock.getInInterceptors()).thenReturn(inInterceptors);
            when(mock.getOutFaultInterceptors()).thenReturn(outFaultInterceptors);
            when(mock.getInFaultInterceptors()).thenReturn(inFaultInterceptors);
        }
    }
}
