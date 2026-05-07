package ir.bita.esb.cxf;

import org.apache.camel.component.cxf.CxfEndpointConfigurer;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
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
 * Creates CxfEndpointConfigurer from config map (bitaKeystorePropsPath, clientTruststorePropsPath, password).
 * Used when RouteDefinition.inputConfig or outputConfig contains wsSecurity.
 */
public final class WsSecurityCxfConfigurerFactory {

    private WsSecurityCxfConfigurerFactory() {
    }

    public static final String BITA_KEYSTORE_PROPS_PATH = "bitaKeystorePropsPath";
    public static final String CLIENT_TRUSTSTORE_PROPS_PATH = "clientTruststorePropsPath";
    public static final String PASSWORD = "password";

    public static CxfEndpointConfigurer create(Map<String, Object> wsSecurityConfig) {
        String bitaKeystoreProps = getString(wsSecurityConfig, BITA_KEYSTORE_PROPS_PATH);
        String clientTruststoreProps = getString(wsSecurityConfig, CLIENT_TRUSTSTORE_PROPS_PATH);
        String password = getString(wsSecurityConfig, PASSWORD);
        if (password == null) {
            password = "";
        }
        return create(bitaKeystoreProps, clientTruststoreProps, password);
    }

    public static CxfEndpointConfigurer create(String bitaKeystorePropsPath, String clientTruststorePropsPath,
                                              String password) {
        CallbackHandler passwordCallback = new CallbackHandler() {
            @Override
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback cb : callbacks) {
                    if (cb instanceof org.apache.wss4j.common.ext.WSPasswordCallback pc) {
                        pc.setPassword(password);
                    }
                }
            }
        };

        return new CxfEndpointConfigurer() {
            @Override
            public void configureServer(Server server) {
                Map<String, Object> inProps = new HashMap<>();
                inProps.put(ConfigurationConstants.ACTION,
                        ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.TIMESTAMP + " "
                                + ConfigurationConstants.ENCRYPTION);
                inProps.put(ConfigurationConstants.PW_CALLBACK_REF, passwordCallback);
                inProps.put(ConfigurationConstants.SIG_PROP_FILE, clientTruststorePropsPath);
                inProps.put(ConfigurationConstants.DEC_PROP_FILE, bitaKeystorePropsPath);
                inProps.put(ConfigurationConstants.SIG_ALGO, WSS4JConstants.RSA_SHA256);
                inProps.put(ConfigurationConstants.SIG_C14N_ALGO, WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
                inProps.put(ConfigurationConstants.SIG_DIGEST_ALGO, WSS4JConstants.SHA256);
                inProps.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSA15);
                inProps.put(ConfigurationConstants.ENC_SYM_ALGO, WSS4JConstants.AES_256);
                inProps.put(ConfigurationConstants.ENCRYPTION_PARTS,
                        "{Element}{" + WSS4JConstants.SIG_NS + "}" + WSS4JConstants.SIG_LN
                                + ";{Content}{" + WSS4JConstants.URI_SOAP12_ENV + "}" + WSS4JConstants.ELEM_BODY);
                inProps.put(ConfigurationConstants.SIGNATURE_PARTS, signatureParts(false));
                inProps.put(ConfigurationConstants.OPTIONAL_SIGNATURE_PARTS, optionalSignatureParts(false));
                inProps.put(ConfigurationConstants.IS_BSP_COMPLIANT, "false");

                Map<String, Object> outProps = new HashMap<>();
                outProps.put(ConfigurationConstants.ACTION,
                        ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.SIGNATURE + " "
                                + ConfigurationConstants.ENCRYPTION);
                outProps.put(ConfigurationConstants.PW_CALLBACK_REF, passwordCallback);
                outProps.put(ConfigurationConstants.ENCRYPTION_USER, ConfigurationConstants.USE_REQ_SIG_CERT);
                outProps.put(ConfigurationConstants.SIG_PROP_FILE, bitaKeystorePropsPath);
                outProps.put(ConfigurationConstants.ENC_PROP_FILE, clientTruststorePropsPath);
                outProps.put(ConfigurationConstants.SIG_ALGO, WSS4JConstants.RSA_SHA256);
                outProps.put(ConfigurationConstants.SIG_C14N_ALGO, WSS4JConstants.C14N_EXCL_OMIT_COMMENTS);
                outProps.put(ConfigurationConstants.SIG_DIGEST_ALGO, WSS4JConstants.SHA256);
                outProps.put(ConfigurationConstants.ENC_KEY_TRANSPORT, WSS4JConstants.KEYTRANSPORT_RSA15);
                outProps.put(ConfigurationConstants.ENC_SYM_ALGO, WSS4JConstants.AES_256);
                outProps.put(ConfigurationConstants.ENCRYPTION_PARTS,
                        "{Element}{" + WSS4JConstants.SIG_NS + "}" + WSS4JConstants.SIG_LN
                                + ";{Content}{" + WSS4JConstants.URI_SOAP12_ENV + "}" + WSS4JConstants.ELEM_BODY);
                outProps.put(ConfigurationConstants.SIGNATURE_PARTS, signatureParts(true));
                outProps.put(ConfigurationConstants.OPTIONAL_SIGNATURE_PARTS, optionalSignatureParts(true));

                server.getEndpoint().getInInterceptors().add(new WSS4JInInterceptor(inProps));
                server.getEndpoint().getInFaultInterceptors().add(new WSS4JInInterceptor(inProps));
                server.getEndpoint().getOutInterceptors().add(new WSS4JOutInterceptor(outProps));
                server.getEndpoint().getOutFaultInterceptors().add(new WSS4JOutInterceptor(outProps));
            }

            @Override
            public void configureClient(Client client) {
            }

            private String signatureParts(boolean isOut) {
                String wsa = "http://www.w3.org/2005/08/addressing";
                String wsu = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
                String soap = "http://www.w3.org/2003/05/soap-envelope";
                String res = "{}" + "{" + wsa + "}MessageID;{}" + "{" + wsa + "}To;";
                if (isOut) res += "{}" + "{" + wsa + "}RelatesTo;{}" + "{" + wsa + "}From;";
                res += "{}" + "{" + wsa + "}Action;{}" + "{" + wsu + "}Timestamp;{}" + "{" + soap + "}Body";
                return res;
            }

            private String optionalSignatureParts(boolean isOut) {
                String wsa = "http://www.w3.org/2005/08/addressing";
                String res = "{}" + "{" + wsa + "}ReplyTo;{}" + "{" + wsa + "}FaultTo;";
                if (!isOut) res += "{}" + "{" + wsa + "}RelatesTo;";
                return res;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        return v.toString();
    }
}
