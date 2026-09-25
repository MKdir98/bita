package ir.bita.esb.cxf;

import org.apache.camel.component.cxf.jaxws.CxfConfigurer;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.WSS4JConstants;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates {@link CxfConfigurer} instances from a config map.
 * Used when RouteDefinition.inputConfig or outputConfig contains a {@code wsSecurity} entry.
 *
 * <p>Aligned with legacy BITA WS-Security policy: Timestamp + Signature + Encryption,
 * RSA-SHA256, AES-256, RSA key transport.</p>
 *
 * <p>For <strong>outbound</strong> CXF (gateway as SOAP client to a third-party secured provider), use
 * {@link #createSoapClientConfigurer(Map)} with {@link #SOAP_CLIENT_SIGNATURE_PROPS_FILE} and
 * {@link #BITA_TRUSTSTORE_PROPS_PATH} set in the wsSecurity map.</p>
 */
public final class WsSecurityCxfConfigurerFactory {

    private WsSecurityCxfConfigurerFactory() {
    }

    public static final String BITA_KEYSTORE_PROPS_PATH = "bitaKeystorePropsPath";
    public static final String CLIENT_TRUSTSTORE_PROPS_PATH = "clientTruststorePropsPath";
    public static final String BITA_TRUSTSTORE_PROPS_PATH = "bitaTruststorePropsPath";
    public static final String PASSWORD = "password";

    /**
     * Properties file for the SOAP <strong>client</strong> signing keystore (gateway identity when calling provider).
     * When present on output CXF config, {@link CxfConfigurer#configureClient} is wired for
     * WS-Security (request signing/encryption + response verify/decrypt).
     */
    public static final String SOAP_CLIENT_SIGNATURE_PROPS_FILE = "soapClientSignaturePropsFile";

    public static CxfConfigurer create(Map<String, Object> wsSecurityConfig) {
        String bitaKeystoreProps = getString(wsSecurityConfig, BITA_KEYSTORE_PROPS_PATH);
        String clientTruststoreProps = getString(wsSecurityConfig, CLIENT_TRUSTSTORE_PROPS_PATH);
        String rawPw = getString(wsSecurityConfig, PASSWORD);
        String password = rawPw != null ? rawPw : "";
        return create(bitaKeystoreProps, clientTruststoreProps, password);
    }

    /**
     * WS-Security for a Camel CXF <strong>producer</strong> calling a BITA-style secured SOAP endpoint
     * (same policy family as legacy {@code Service#fillInPropertiesHashMap} / {@code fillOutPropertiesHashMap}).
     */
    public static CxfConfigurer createSoapClientConfigurer(Map<String, Object> wsSecurityConfig) {
        String soapClientSigProps = getString(wsSecurityConfig, SOAP_CLIENT_SIGNATURE_PROPS_FILE);
        String bitaTruststoreProps = getString(wsSecurityConfig, BITA_TRUSTSTORE_PROPS_PATH);
        String rawPw = getString(wsSecurityConfig, PASSWORD);
        String password = rawPw != null ? rawPw : "";
        if (soapClientSigProps == null || bitaTruststoreProps == null) {
            throw new IllegalArgumentException("wsSecurity map must include "
                    + SOAP_CLIENT_SIGNATURE_PROPS_FILE + " and " + BITA_TRUSTSTORE_PROPS_PATH
                    + " for outbound SOAP WS-Security");
        }

        CallbackHandler pwCb = callbacks -> {
            for (Callback cb : callbacks)
                if (cb instanceof org.apache.wss4j.common.ext.WSPasswordCallback pc)
                    pc.setPassword(password);
        };

        return new CxfConfigurer() {
            @Override
            public void configureServer(Server server) {}

            @Override
            public void configure(org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory factoryBean) {

            }

            @Override
            public void configureClient(Client client) {
                Map<String, Object> out = new HashMap<>();
                out.put(ConfigurationConstants.ACTION,
                        ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.SIGNATURE + " "
                                + ConfigurationConstants.ENCRYPTION);
                out.put(ConfigurationConstants.PW_CALLBACK_REF, pwCb);
                out.put(ConfigurationConstants.SIG_PROP_FILE, soapClientSigProps);
                out.put(ConfigurationConstants.ENC_PROP_FILE, bitaTruststoreProps);
                out.put(ConfigurationConstants.ENCRYPTION_USER, "bita");
                out.put(ConfigurationConstants.SIG_ALGO, WSS4JConstants.RSA_SHA256);
                out.put(ConfigurationConstants.SIG_C14N_ALGO, WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
                out.put(ConfigurationConstants.SIG_DIGEST_ALGO, WSS4JConstants.SHA256);
                out.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSA15);
                out.put(ConfigurationConstants.ENC_SYM_ALGO, WSS4JConstants.AES_256);

                Map<String, Object> in = new HashMap<>();
                in.put(ConfigurationConstants.ACTION,
                        ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.TIMESTAMP + " "
                                + ConfigurationConstants.ENCRYPTION);
                in.put(ConfigurationConstants.PW_CALLBACK_REF, pwCb);
                in.put(ConfigurationConstants.SIG_PROP_FILE, bitaTruststoreProps);
                in.put(ConfigurationConstants.DEC_PROP_FILE, soapClientSigProps);
                in.put(ConfigurationConstants.SIG_ALGO, WSS4JConstants.RSA_SHA256);
                in.put(ConfigurationConstants.SIG_C14N_ALGO, WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
                in.put(ConfigurationConstants.SIG_DIGEST_ALGO, WSS4JConstants.SHA256);
                in.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSA15);
                in.put(ConfigurationConstants.ENC_SYM_ALGO, WSS4JConstants.AES_256);
                in.put(ConfigurationConstants.IS_BSP_COMPLIANT, "false");

                client.getOutInterceptors().add(new WSS4JOutInterceptor(out));
                client.getOutFaultInterceptors().add(new WSS4JOutInterceptor(out));
                client.getInInterceptors().add(new WSS4JInInterceptor(in));
                client.getInFaultInterceptors().add(new WSS4JInInterceptor(in));
            }
        };
    }

    public static CxfConfigurer create(String bitaKeystorePropsPath, String clientTruststorePropsPath,
                                       String password) {
        CallbackHandler pwCb = callbacks -> {
            for (Callback cb : callbacks)
                if (cb instanceof org.apache.wss4j.common.ext.WSPasswordCallback pc)
                    pc.setPassword(password);
        };

        return new CxfConfigurer() {
            @Override
            public void configure(AbstractWSDLBasedEndpointFactory factory) {}

            @Override
            public void configureServer(Server server) {
                Map<String, Object> inProps = new HashMap<>();
                inProps.put(ConfigurationConstants.ACTION,
                        ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.TIMESTAMP + " "
                                + ConfigurationConstants.ENCRYPTION);
                inProps.put(ConfigurationConstants.PW_CALLBACK_REF, pwCb);
                inProps.put(ConfigurationConstants.SIG_PROP_FILE, clientTruststorePropsPath);
                inProps.put(ConfigurationConstants.DEC_PROP_FILE, bitaKeystorePropsPath);
                inProps.put(ConfigurationConstants.SIG_ALGO, WSS4JConstants.RSA_SHA256);
                inProps.put(ConfigurationConstants.SIG_C14N_ALGO, WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
                inProps.put(ConfigurationConstants.SIG_DIGEST_ALGO, WSS4JConstants.SHA256);
                inProps.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSA15);
                inProps.put(ConfigurationConstants.ENC_SYM_ALGO, WSS4JConstants.AES_256);
                inProps.put(ConfigurationConstants.IS_BSP_COMPLIANT, "false");

                Map<String, Object> outProps = new HashMap<>();
                outProps.put(ConfigurationConstants.ACTION,
                        ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.SIGNATURE + " "
                                + ConfigurationConstants.ENCRYPTION);
                outProps.put(ConfigurationConstants.PW_CALLBACK_REF, pwCb);
                outProps.put(ConfigurationConstants.ENCRYPTION_USER, ConfigurationConstants.USE_REQ_SIG_CERT);
                outProps.put(ConfigurationConstants.SIG_PROP_FILE, bitaKeystorePropsPath);
                outProps.put(ConfigurationConstants.ENC_PROP_FILE, clientTruststorePropsPath);
                outProps.put(ConfigurationConstants.SIG_ALGO, WSS4JConstants.RSA_SHA256);
                outProps.put(ConfigurationConstants.SIG_C14N_ALGO, WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
                outProps.put(ConfigurationConstants.SIG_DIGEST_ALGO, WSS4JConstants.SHA256);
                outProps.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSA15);
                outProps.put(ConfigurationConstants.ENC_SYM_ALGO, WSS4JConstants.AES_256);

                server.getEndpoint().getInInterceptors().add(new WSS4JInInterceptor(inProps));
                server.getEndpoint().getInFaultInterceptors().add(new WSS4JInInterceptor(inProps));
                server.getEndpoint().getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
                server.getEndpoint().getOutFaultInterceptors().add(new WSS4JOutInterceptor(outProps));
            }

            @Override
            public void configureClient(Client client) {}
        };
    }

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
