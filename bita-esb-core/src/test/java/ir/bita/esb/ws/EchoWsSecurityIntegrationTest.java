package ir.bita.esb.ws;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import ir.bita.esb.access.RouteAccessService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three-organisation WS-Security integration test.
 *
 * <p>The gateway route is loaded from {@code echo-gw-route.groovy} — a Groovy script representing
 * what an ESM user stores in the DB. ESB injects a {@link RouteAccessService} into the binding
 * so the script resolves keystore paths and enforces access control without ESB-specific code.
 */
@DisplayName("Three-org WS-Security: Client → Gateway (ESB) → Provider")
class EchoWsSecurityIntegrationTest {

    private static final int PROVIDER_PORT = 29_446;
    private static final int GATEWAY_PORT  = 29_447;

    @TempDir Path tempDir;

    private TestKeyStoreGenerator.ThreePartyKeyStores ks;
    private Server              providerServer;
    private DefaultCamelContext gatewayContext;
    private EchoService         clientProxy;
    private String              gwWsdlUri;

    @BeforeEach
    void setUp() throws Exception {
        ks = TestKeyStoreGenerator.generateThreeParty(tempDir);

        String pvWsdlUri = Path.of(getClass().getResource("/ws-security/echo.wsdl").toURI()).toUri().toString();
        String gwAddress = "http://127.0.0.1:" + GATEWAY_PORT  + "/echo";
        String pvAddress = "http://127.0.0.1:" + PROVIDER_PORT + "/echo";

        // ── 1. PROVIDER ─────────────────────────────────────────────────────────────────────────
        providerServer = buildProviderServer(pvAddress);
        Thread.sleep(1000);

        // ── 2. GATEWAY: load Groovy script (as if read from DB), inject RouteAccessService ─────
        String routeScript = new String(
                getClass().getResourceAsStream("/ws-security/echo-gw-route.groovy").readAllBytes(),
                StandardCharsets.UTF_8);

        // Test implementation of RouteAccessService backed by ThreePartyKeyStores.
        // In production this is DefaultRouteAccessService backed by AccessCache + ClientCache.
        RouteAccessService accessService = new RouteAccessService() {
            public String getBitaKeyStore()       { return ks.gwKeystorePropsPath(); }
            public String getBitaPassword()       { return TestKeyStoreGenerator.PASSWORD; }
            public String getClientTrustStore()   { return ks.gwIncomingTrustPropsPath(); }
            public List<String> getClientKeys()   { return List.of(); }
            public String getGatewayAddress()     { return gwAddress; }
            public String getProviderTrustStore() { return ks.gwOutgoingTrustPropsPath(); }
            public String getProviderAlias()      { return TestKeyStoreGenerator.PROVIDER_ALIAS; }
            public boolean hasAccess(String clientId)      { return true; }
            public boolean checkRateLimit(String clientId) { return true; }
            public String getClientIdByApiKey(String k)    { return null; }
            public String getClientIdByIp(String ip)       { return null; }
        };

        Binding binding = new Binding();
        binding.setVariable("accessService", accessService);
        binding.setVariable("pvAddress",     pvAddress);
        binding.setVariable("pvWsdlUri",      pvWsdlUri);

        gatewayContext = new DefaultCamelContext();
        RouteBuilder rb = (RouteBuilder) new GroovyShell(
                Thread.currentThread().getContextClassLoader(), binding).evaluate(routeScript);
        rb.setCamelContext(gatewayContext);
        gatewayContext.addRoutes(rb);
        gatewayContext.start();
        Thread.sleep(2000);

        gwWsdlUri = gatewayContext.getRegistry().lookupByNameAndType("echo-gw-wsdl-uri", String.class);

        // ── 3. CLIENT ────────────────────────────────────────────────────────────────────────────
        clientProxy = buildClientProxy(gwAddress);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (gatewayContext != null) gatewayContext.stop();
        if (providerServer  != null) providerServer.stop();
    }

    @Test
    @DisplayName("T1: basic echo — 'I get' + message returned through full three-org WS-Security chain")
    void shouldEchoMessageThroughWsSecurityChain() {
        assertThat(clientProxy.echo("hello", 0)).isEqualTo("I gethello");
    }

    @Test
    @DisplayName("T2: echo with delay — provider sleeps the requested seconds")
    void shouldDelayResponseBySpecifiedSeconds() {
        long start = System.currentTimeMillis();
        assertThat(clientProxy.echo("world", 1)).isEqualTo("I getworld");
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(1000L);
    }

    @Test
    @DisplayName("T3: generated client WSDL contains bita WS-Security policy")
    void generatedClientWsdlShouldContainPolicy() throws Exception {
        assertThat(gwWsdlUri).isNotNull();
        String content = Files.readString(Path.of(new URI(gwWsdlUri)));
        assertThat(content)
                .contains("Service_Binding_Policy")
                .contains("Service_Input_Policy")
                .contains("Service_Output_Policy")
                .contains("AsymmetricBinding")
                .contains("PolicyReference");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private Server buildProviderServer(String address) {
        var pwCb = pwCallback(TestKeyStoreGenerator.PASSWORD);

        Map<String, Object> pvIn = new HashMap<>();
        pvIn.put(ConfigurationConstants.ACTION,
                ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.ENCRYPTION);
        pvIn.put(ConfigurationConstants.PW_CALLBACK_REF,  pwCb);
        pvIn.put(ConfigurationConstants.SIG_PROP_FILE,    ks.providerTrustPropsPath());
        pvIn.put(ConfigurationConstants.DEC_PROP_FILE,    ks.providerKeystorePropsPath());
        pvIn.put(ConfigurationConstants.SIG_ALGO,         WSS4JConstants.RSA_SHA256);
        pvIn.put(ConfigurationConstants.SIG_C14N_ALGO,    WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
        pvIn.put(ConfigurationConstants.SIG_DIGEST_ALGO,  WSS4JConstants.SHA256);
        pvIn.put(ConfigurationConstants.ENC_SYM_ALGO,     WSS4JConstants.AES_256);
        pvIn.put(ConfigurationConstants.IS_BSP_COMPLIANT, "false");

        Map<String, Object> pvOut = new HashMap<>();
        pvOut.put(ConfigurationConstants.ACTION,
                ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.ENCRYPTION);
        pvOut.put(ConfigurationConstants.PW_CALLBACK_REF,   pwCb);
        pvOut.put(ConfigurationConstants.SIG_PROP_FILE,     ks.providerKeystorePropsPath());
        pvOut.put(ConfigurationConstants.ENC_PROP_FILE,     ks.providerTrustPropsPath());
        pvOut.put(ConfigurationConstants.ENCRYPTION_USER,   ConfigurationConstants.USE_REQ_SIG_CERT);
        pvOut.put(ConfigurationConstants.SIGNATURE_USER,   TestKeyStoreGenerator.PROVIDER_ALIAS);
        pvOut.put(ConfigurationConstants.SIG_ALGO,          WSS4JConstants.RSA_SHA256);
        pvOut.put(ConfigurationConstants.SIG_C14N_ALGO,     WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
        pvOut.put(ConfigurationConstants.SIG_DIGEST_ALGO,   WSS4JConstants.SHA256);
        pvOut.put(ConfigurationConstants.ENC_SYM_ALGO,      WSS4JConstants.AES_256);
        pvOut.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSAOAEP);
        pvOut.put(ConfigurationConstants.ENC_KEY_ID,        "IssuerSerial");

        Bus providerBus = BusFactory.newInstance().createBus();
        JaxWsServerFactoryBean sf = new JaxWsServerFactoryBean();
        sf.setBus(providerBus);
        sf.setServiceClass(EchoService.class);
        sf.setServiceBean(new EchoServiceImpl());
        sf.setAddress(address);
        sf.getInInterceptors().add(new WSS4JInInterceptor(pvIn));
        sf.getInFaultInterceptors().add(new WSS4JInInterceptor(pvIn));
        sf.getOutInterceptors().add(new WSS4JOutInterceptor(pvOut));
        sf.getOutFaultInterceptors().add(new WSS4JOutInterceptor(pvOut));
        return sf.create();
    }

    private EchoService buildClientProxy(String gwAddress) {
        var pwCb = pwCallback(TestKeyStoreGenerator.PASSWORD);

        Map<String, Object> clOut = new HashMap<>();
        clOut.put(ConfigurationConstants.ACTION,
                ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.ENCRYPTION);
        clOut.put(ConfigurationConstants.USER,              TestKeyStoreGenerator.CLIENT_ALIAS);
        clOut.put(ConfigurationConstants.PW_CALLBACK_REF,   pwCb);
        clOut.put(ConfigurationConstants.SIG_PROP_FILE,     ks.clientKeystorePropsPath());
        clOut.put(ConfigurationConstants.ENC_PROP_FILE,     ks.clientTrustPropsPath());
        clOut.put(ConfigurationConstants.ENCRYPTION_USER,   TestKeyStoreGenerator.BITA_ALIAS);
        clOut.put(ConfigurationConstants.SIG_ALGO,          WSS4JConstants.RSA_SHA256);
        clOut.put(ConfigurationConstants.SIG_C14N_ALGO,     WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
        clOut.put(ConfigurationConstants.SIG_DIGEST_ALGO,   WSS4JConstants.SHA256);
        clOut.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSAOAEP);
        clOut.put(ConfigurationConstants.ENC_SYM_ALGO,      WSS4JConstants.AES_256);
        clOut.put(ConfigurationConstants.ENC_KEY_ID,        "IssuerSerial");

        Map<String, Object> clIn = new HashMap<>();
        clIn.put(ConfigurationConstants.ACTION,
                ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.ENCRYPTION);
        clIn.put(ConfigurationConstants.PW_CALLBACK_REF,   pwCb);
        clIn.put(ConfigurationConstants.SIG_PROP_FILE,     ks.clientTrustPropsPath());
        clIn.put(ConfigurationConstants.DEC_PROP_FILE,     ks.clientKeystorePropsPath());
        clIn.put(ConfigurationConstants.IS_BSP_COMPLIANT,  "false");

        JaxWsProxyFactoryBean cf = new JaxWsProxyFactoryBean();
        cf.setServiceClass(EchoService.class);
        cf.setAddress(gwAddress);
        cf.getFeatures().add(new LoggingFeature());
        cf.getOutInterceptors().add(new WSS4JOutInterceptor(clOut));
        cf.getInInterceptors().add(new WSS4JInInterceptor(clIn));
        cf.getInFaultInterceptors().add(new WSS4JInInterceptor(clIn));
        return (EchoService) cf.create();
    }

    private static javax.security.auth.callback.CallbackHandler pwCallback(String password) {
        return callbacks -> {
            for (var cb : callbacks)
                if (cb instanceof WSPasswordCallback pc) pc.setPassword(password);
        };
    }
}
