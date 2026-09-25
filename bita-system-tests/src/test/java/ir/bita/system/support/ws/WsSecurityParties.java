package ir.bita.system.support.ws;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.common.ext.WSPasswordCallback;

import javax.security.auth.callback.CallbackHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The two organisations around the gateway in a WS-Security test: a provider organisation
 * running a real CXF service with WSS4J, and a consumer organisation calling through the
 * gateway with its own key. Keys and trust stores come from {@link TestKeyStoreGenerator}.
 */
public final class WsSecurityParties {

    private final TestKeyStoreGenerator.ThreePartyKeyStores ks;

    public WsSecurityParties(Path dir) throws Exception {
        this.ks = TestKeyStoreGenerator.generateThreeParty(dir);
    }

    public TestKeyStoreGenerator.ThreePartyKeyStores keyStores() {
        return ks;
    }

    /** The consumer certificate, as an organisation would register it with ESM. */
    public String consumerCertificatePem() throws Exception {
        return Files.readString(ks.baseDir().resolve("client.pem"));
    }

    /**
     * A provider that requires its requests signed (and encrypted, if {@code encrypt}) by the
     * gateway's key, and answers the same way.
     */
    public Server startProvider(String address, boolean encrypt) {
        CallbackHandler pw = password();
        String actionsIn = ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.TIMESTAMP
                + (encrypt ? " " + ConfigurationConstants.ENCRYPTION : "");
        String actionsOut = ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.SIGNATURE
                + (encrypt ? " " + ConfigurationConstants.ENCRYPTION : "");

        Map<String, Object> in = new HashMap<>();
        in.put(ConfigurationConstants.ACTION, actionsIn);
        in.put(ConfigurationConstants.PW_CALLBACK_REF, pw);
        in.put(ConfigurationConstants.SIG_PROP_FILE, ks.providerTrustPropsPath());
        in.put(ConfigurationConstants.DEC_PROP_FILE, ks.providerKeystorePropsPath());
        in.put(ConfigurationConstants.IS_BSP_COMPLIANT, "false");

        Map<String, Object> out = new HashMap<>();
        out.put(ConfigurationConstants.ACTION, actionsOut);
        out.put(ConfigurationConstants.PW_CALLBACK_REF, pw);
        out.put(ConfigurationConstants.SIGNATURE_USER, TestKeyStoreGenerator.PROVIDER_ALIAS);
        out.put(ConfigurationConstants.SIG_PROP_FILE, ks.providerKeystorePropsPath());
        out.put(ConfigurationConstants.SIG_ALGO, WSS4JConstants.RSA_SHA256);
        out.put(ConfigurationConstants.SIG_C14N_ALGO, WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
        out.put(ConfigurationConstants.SIG_DIGEST_ALGO, WSS4JConstants.SHA256);
        if (encrypt) {
            out.put(ConfigurationConstants.ENC_PROP_FILE, ks.providerTrustPropsPath());
            out.put(ConfigurationConstants.ENCRYPTION_USER, ConfigurationConstants.USE_REQ_SIG_CERT);
            out.put(ConfigurationConstants.ENC_SYM_ALGO, WSS4JConstants.AES_256);
            out.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSAOAEP);
            out.put(ConfigurationConstants.ENC_KEY_ID, "IssuerSerial");
        }

        Bus bus = BusFactory.newInstance().createBus();
        JaxWsServerFactoryBean sf = new JaxWsServerFactoryBean();
        sf.setBus(bus);
        sf.setServiceClass(EchoService.class);
        sf.setServiceBean(new EchoServiceImpl());
        sf.setAddress(address);
        sf.getInInterceptors().add(new WSS4JInInterceptor(in));
        sf.getInFaultInterceptors().add(new WSS4JInInterceptor(in));
        sf.getOutInterceptors().add(new WSS4JOutInterceptor(out));
        sf.getOutFaultInterceptors().add(new WSS4JOutInterceptor(out));
        return sf.create();
    }

    /** A consumer that signs + encrypts to the gateway (BITA policy) and verifies its replies. */
    public EchoService consumer(String gatewayAddress) {
        CallbackHandler pw = password();
        Map<String, Object> out = new HashMap<>();
        out.put(ConfigurationConstants.ACTION, ConfigurationConstants.TIMESTAMP + " "
                + ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.ENCRYPTION);
        out.put(ConfigurationConstants.USER, TestKeyStoreGenerator.CLIENT_ALIAS);
        out.put(ConfigurationConstants.PW_CALLBACK_REF, pw);
        out.put(ConfigurationConstants.SIG_PROP_FILE, ks.clientKeystorePropsPath());
        out.put(ConfigurationConstants.ENC_PROP_FILE, ks.clientTrustPropsPath());
        out.put(ConfigurationConstants.ENCRYPTION_USER, TestKeyStoreGenerator.BITA_ALIAS);
        out.put(ConfigurationConstants.SIG_ALGO, WSS4JConstants.RSA_SHA256);
        out.put(ConfigurationConstants.SIG_C14N_ALGO, WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
        out.put(ConfigurationConstants.SIG_DIGEST_ALGO, WSS4JConstants.SHA256);
        out.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSAOAEP);
        out.put(ConfigurationConstants.ENC_SYM_ALGO, WSS4JConstants.AES_256);
        out.put(ConfigurationConstants.ENC_KEY_ID, "IssuerSerial");

        Map<String, Object> in = new HashMap<>();
        in.put(ConfigurationConstants.ACTION, ConfigurationConstants.SIGNATURE + " "
                + ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.ENCRYPTION);
        in.put(ConfigurationConstants.PW_CALLBACK_REF, pw);
        in.put(ConfigurationConstants.SIG_PROP_FILE, ks.clientTrustPropsPath());
        in.put(ConfigurationConstants.DEC_PROP_FILE, ks.clientKeystorePropsPath());
        in.put(ConfigurationConstants.IS_BSP_COMPLIANT, "false");

        JaxWsProxyFactoryBean cf = new JaxWsProxyFactoryBean();
        cf.setServiceClass(EchoService.class);
        cf.setAddress(gatewayAddress);
        cf.getOutInterceptors().add(new WSS4JOutInterceptor(out));
        cf.getInInterceptors().add(new WSS4JInInterceptor(in));
        cf.getInFaultInterceptors().add(new WSS4JInInterceptor(in));
        return (EchoService) cf.create();
    }

    private static CallbackHandler password() {
        return callbacks -> {
            for (var cb : callbacks) {
                if (cb instanceof WSPasswordCallback pc) {
                    pc.setPassword(TestKeyStoreGenerator.PASSWORD);
                }
            }
        };
    }
}
