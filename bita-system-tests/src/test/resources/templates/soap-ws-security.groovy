/**
 * GroovyTemplate: SOAP service with WS-Security 1.1 on both legs.
 *
 * Variables (from service_groovy_config): pvAddress, pvWsdlUri, gwPort, gwPath,
 *   securityPolicy       — what the provider requires: "Timestamp+Signature" or
 *                          "Timestamp+Signature+Encryption"
 *   signaturePropsFile   — Merlin properties of the key the gateway signs with toward the provider
 *   truststorePropsFile  — Merlin properties of the store holding the provider's certificate
 *   (relative file names resolve against the directory of the ESB's own keystore properties)
 * ESB bindings: accessService, vertxInstance, gatewayRouter.
 *
 * Consumer leg: BITA's standard policy (sign + encrypt + timestamp); consumers are trusted if
 * their certificate is among the ones ESM synced for this service (clients granted access).
 */
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.cxf.jaxws.CxfConfigurer
import org.apache.cxf.Bus
import org.apache.cxf.BusFactory
import org.apache.cxf.endpoint.Client
import org.apache.cxf.endpoint.Server
import org.apache.cxf.ext.logging.LoggingInInterceptor
import org.apache.cxf.ext.logging.LoggingOutInterceptor
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory
import org.apache.cxf.ws.policy.PolicyEngine
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor
import org.apache.wss4j.common.ConfigurationConstants
import org.apache.wss4j.common.WSS4JConstants

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Node
import org.xml.sax.InputSource

class WsdlTransformer {

    private static final String WSDL_NS = "http://schemas.xmlsoap.org/wsdl/"
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/wsdl/soap/"
    private static final String WSP_NS  = "http://schemas.xmlsoap.org/ws/2004/09/policy"

    private static final String BITA_POLICY = """
<policies xmlns:wsp="http://schemas.xmlsoap.org/ws/2004/09/policy"
          xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
          xmlns:sp="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702"
          xmlns:wsaw="http://www.w3.org/2006/05/addressing/wsdl">
  <wsp:Policy wsu:Id="Service_Binding_Policy">
    <wsp:ExactlyOne><wsp:All>
      <sp:AsymmetricBinding>
        <wsp:Policy>
          <sp:InitiatorToken><wsp:Policy>
            <sp:X509Token sp:IncludeToken="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702/IncludeToken/AlwaysToRecipient">
              <wsp:Policy><sp:WssX509V3Token10/></wsp:Policy>
            </sp:X509Token>
          </wsp:Policy></sp:InitiatorToken>
          <sp:RecipientToken><wsp:Policy>
            <sp:X509Token sp:IncludeToken="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702/IncludeToken/Never">
              <wsp:Policy><sp:WssX509V3Token10/></wsp:Policy>
            </sp:X509Token>
          </wsp:Policy></sp:RecipientToken>
          <sp:AlgorithmSuite><wsp:Policy><sp:Basic256Sha256/></wsp:Policy></sp:AlgorithmSuite>
          <sp:Layout><wsp:Policy><sp:Lax/></wsp:Policy></sp:Layout>
          <sp:IncludeTimestamp/>
          <sp:EncryptSignature/>
          <sp:OnlySignEntireHeadersAndBody/>
        </wsp:Policy>
      </sp:AsymmetricBinding>
      <sp:Wss10>
        <wsp:Policy>
          <sp:MustSupportRefKeyIdentifier/>
          <sp:MustSupportRefIssuerSerial/>
        </wsp:Policy>
      </sp:Wss10>
    </wsp:All></wsp:ExactlyOne>
  </wsp:Policy>
  <wsp:Policy wsu:Id="Service_Input_Policy">
    <wsp:ExactlyOne><wsp:All>
      <sp:SignedParts><sp:Body/></sp:SignedParts>
      <sp:EncryptedParts><sp:Body/></sp:EncryptedParts>
    </wsp:All></wsp:ExactlyOne>
  </wsp:Policy>
  <wsp:Policy wsu:Id="Service_Output_Policy">
    <wsp:ExactlyOne><wsp:All>
      <sp:SignedParts><sp:Body/></sp:SignedParts>
      <sp:EncryptedParts><sp:Body/></sp:EncryptedParts>
    </wsp:All></wsp:ExactlyOne>
  </wsp:Policy>
</policies>"""

    static String generateClientWsdl(String pvWsdlUri, String gwAddress) {
        def factory = DocumentBuilderFactory.newInstance()
        factory.namespaceAware = true
        def builder = factory.newDocumentBuilder()
        def doc = builder.parse(pvWsdlUri)
        def root = doc.documentElement

        // Ensure policy-related namespace declarations on root
        root.setAttribute("xmlns:wsp",  "http://schemas.xmlsoap.org/ws/2004/09/policy")
        root.setAttribute("xmlns:wsu",  "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd")
        root.setAttribute("xmlns:sp",   "http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702")
        root.setAttribute("xmlns:wsaw", "http://www.w3.org/2006/05/addressing/wsdl")

        // Replace soap:address with gateway address
        def addresses = doc.getElementsByTagNameNS(SOAP_NS, "address")
        (0..<addresses.length).each { addresses.item(it).setAttribute("location", gwAddress) }

        // Inject PolicyReference into each wsdl:binding
        def bindings = doc.getElementsByTagNameNS(WSDL_NS, "binding")
        (0..<bindings.length).each { bi ->
            def binding = bindings.item(bi)

            def bindingRef = doc.createElementNS(WSP_NS, "wsp:PolicyReference")
            bindingRef.setAttribute("URI", "#Service_Binding_Policy")
            binding.appendChild(bindingRef)

            def ops = binding.getElementsByTagNameNS(WSDL_NS, "operation")
            (0..<ops.length).each { oi ->
                def op = ops.item(oi)

                def inputs = op.getElementsByTagNameNS(WSDL_NS, "input")
                (0..<inputs.length).each {
                    def ref = doc.createElementNS(WSP_NS, "wsp:PolicyReference")
                    ref.setAttribute("URI", "#Service_Input_Policy")
                    inputs.item(it).appendChild(ref)
                }

                def outputs = op.getElementsByTagNameNS(WSDL_NS, "output")
                (0..<outputs.length).each {
                    def ref = doc.createElementNS(WSP_NS, "wsp:PolicyReference")
                    ref.setAttribute("URI", "#Service_Output_Policy")
                    outputs.item(it).appendChild(ref)
                }
            }
        }

        // Append bita standard policy elements to definitions root
        def policyDoc = factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(BITA_POLICY)))
        def policyChildren = policyDoc.documentElement.childNodes
        (0..<policyChildren.length).each { i ->
            def child = policyChildren.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                root.appendChild(doc.importNode(child, true))
            }
        }

        // Write to temp file
        def tempFile = File.createTempFile("gw-wsdl-", ".wsdl")
        tempFile.deleteOnExit()
        TransformerFactory.newInstance().newTransformer().transform(
                new DOMSource(doc), new StreamResult(tempFile))

        return tempFile.toURI().toString()
    }
}


class EsbServerConfigurer implements CxfConfigurer {
    WSS4JInInterceptor inInterceptor
    WSS4JOutInterceptor outInterceptor

    void configure(AbstractWSDLBasedEndpointFactory f) {}

    void configureClient(Client c) {}

    void configureServer(Server server) {
        def ep = server.getEndpoint()
        ep.getInInterceptors().add(inInterceptor)
        ep.getInFaultInterceptors().add(inInterceptor)
        ep.getOutInterceptors().add(outInterceptor)
        // no WSS4J on the consumer-facing fault chain: a refused request (e.g. unsigned) has no
        // requester certificate to encrypt the fault to, and a failed encryption there makes CXF
        // answer an empty HTTP 200 — the consumer must get a plain SOAP Fault instead
    }
}

class EsbClientConfigurer implements CxfConfigurer {

    WSS4JOutInterceptor outInterceptor
    WSS4JInInterceptor inInterceptor

    void configure(AbstractWSDLBasedEndpointFactory f) {}

    void configureServer(Server s) {}

    void configureClient(Client client) {
        client.getOutInterceptors().add(new LoggingOutInterceptor())
        client.getInInterceptors().add(new LoggingInInterceptor())

        client.getOutInterceptors().add(outInterceptor)
        client.getOutFaultInterceptors().add(outInterceptor)

        client.getInInterceptors().add(inInterceptor)
        client.getInFaultInterceptors().add(inInterceptor)
    }
}

new RouteBuilder() {

    void configure() {

        def secretsDir = new File(accessService.getBitaKeyStore() as String).parentFile
        def resolve = { String f -> new File(f).isAbsolute() ? f : new File(secretsDir, f).absolutePath }
        def aliasIn = { String propsFile ->
            def p = new Properties()
            new File(propsFile).withInputStream { p.load(it) }
            def alias = p.getProperty("org.apache.ws.security.crypto.merlin.keystore.alias")
            if (alias) return alias
            def ks = java.security.KeyStore.getInstance(p.getProperty("org.apache.ws.security.crypto.merlin.keystore.type", "jks"))
            new File(p.getProperty("org.apache.ws.security.crypto.merlin.keystore.file")).withInputStream {
                ks.load(it, p.getProperty("org.apache.ws.security.crypto.merlin.keystore.password").toCharArray())
            }
            ks.aliases().nextElement()
        }

        def bitaKs    = accessService.getBitaKeyStore()
        def clientTs  = accessService.getClientTrustStore()
        def pvSigKs   = resolve(signaturePropsFile as String)
        def provTs    = resolve(truststorePropsFile as String)
        def sigAlias  = aliasIn(pvSigKs)
        def provAlias = aliasIn(provTs)
        def encrypt   = (securityPolicy as String).contains("Encryption")
        def gw        = "http://0.0.0.0:${gwPort}${gwPath}"
        def pv        = pvAddress as String
        def pvWsdl    = pvWsdlUri as String
        def acc       = accessService
        def id        = "wss-" + UUID.randomUUID().toString().substring(0, 8)
        def cb = { callbacks ->
            def pwd = accessService.getBitaPassword()
            callbacks.each { if (it instanceof org.apache.wss4j.common.ext.WSPasswordCallback) it.setPassword(pwd) }
        } as javax.security.auth.callback.CallbackHandler

        if (clientTs == null) {
            throw new IllegalStateException("no consumer certificates synced for this service — grant a client with an X.509 credential first")
        }

        def gwWsdl = WsdlTransformer.generateClientWsdl(pvWsdl, "http://127.0.0.1:${gwPort}${gwPath}")
        getContext().registry.bind(id + "-wsdl-uri", gwWsdl)

        Bus gwBus = BusFactory.newInstance().createBus()
        gwBus.getExtension(PolicyEngine.class).setEnabled(false)
        getContext().registry.bind(id + "-bus", gwBus)
        // the bus owns the gateway's Jetty listener: shut it down with this script version's
        // CamelContext, or the port stays bound after a reload or an ESB shutdown
        getContext().addService(new org.apache.camel.support.service.ServiceSupport() {
            protected void doStop() { gwBus.shutdown(true) }
        })

        // ── consumer leg: inbound decrypt+verify / outbound sign+encrypt back to the requester ──
        def svInMap = new HashMap()
        svInMap[ConfigurationConstants.ACTION] = ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.ENCRYPTION
        svInMap[ConfigurationConstants.PW_CALLBACK_REF] = cb
        svInMap[ConfigurationConstants.SIG_PROP_FILE] = clientTs
        svInMap[ConfigurationConstants.DEC_PROP_FILE] = bitaKs
        svInMap[ConfigurationConstants.IS_BSP_COMPLIANT] = "false"

        def svOutMap = new HashMap()
        svOutMap[ConfigurationConstants.USER] = aliasIn(bitaKs)
        svOutMap[ConfigurationConstants.ACTION] = ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.SIGNATURE + " " + ConfigurationConstants.ENCRYPTION
        svOutMap[ConfigurationConstants.PW_CALLBACK_REF] = cb
        svOutMap[ConfigurationConstants.ENCRYPTION_USER] = ConfigurationConstants.USE_REQ_SIG_CERT
        svOutMap[ConfigurationConstants.SIG_PROP_FILE] = bitaKs
        svOutMap[ConfigurationConstants.ENC_PROP_FILE] = clientTs
        svOutMap[ConfigurationConstants.SIG_ALGO] = WSS4JConstants.RSA_SHA256
        svOutMap[ConfigurationConstants.SIG_C14N_ALGO] = WSS4JConstants.C14N_EXCL_OMIT_COMMENTS
        svOutMap[ConfigurationConstants.SIG_DIGEST_ALGO] = WSS4JConstants.SHA256
        svOutMap[ConfigurationConstants.ENC_KEY_TRANSPORT] = WSS4JConstants.KEYTRANSPORT_RSAOAEP
        svOutMap[ConfigurationConstants.ENC_SYM_ALGO] = WSS4JConstants.AES_256
        svOutMap[ConfigurationConstants.ENC_KEY_ID] = "IssuerSerial"

        // ── provider leg: what the provider's securityPolicy requires ──
        def pvActions = ConfigurationConstants.TIMESTAMP + " " + ConfigurationConstants.SIGNATURE +
                (encrypt ? " " + ConfigurationConstants.ENCRYPTION : "")
        def clOutMap = new HashMap()
        clOutMap[ConfigurationConstants.USER] = sigAlias
        clOutMap[ConfigurationConstants.ACTION] = pvActions
        clOutMap[ConfigurationConstants.PW_CALLBACK_REF] = cb
        clOutMap[ConfigurationConstants.SIG_PROP_FILE] = pvSigKs
        clOutMap[ConfigurationConstants.SIG_ALGO] = WSS4JConstants.RSA_SHA256
        clOutMap[ConfigurationConstants.SIG_C14N_ALGO] = WSS4JConstants.C14N_EXCL_OMIT_COMMENTS
        clOutMap[ConfigurationConstants.SIG_DIGEST_ALGO] = WSS4JConstants.SHA256
        if (encrypt) {
            clOutMap[ConfigurationConstants.ENC_PROP_FILE] = provTs
            clOutMap[ConfigurationConstants.ENCRYPTION_USER] = provAlias
            clOutMap[ConfigurationConstants.ENC_KEY_ID] = "IssuerSerial"
            clOutMap[ConfigurationConstants.ENC_KEY_TRANSPORT] = WSS4JConstants.KEYTRANSPORT_RSAOAEP
            clOutMap[ConfigurationConstants.ENC_SYM_ALGO] = WSS4JConstants.AES_256
        }

        def clInMap = new HashMap()
        clInMap[ConfigurationConstants.ACTION] = pvActions
        clInMap[ConfigurationConstants.PW_CALLBACK_REF] = cb
        clInMap[ConfigurationConstants.SIG_PROP_FILE] = provTs
        if (encrypt) {
            clInMap[ConfigurationConstants.DEC_PROP_FILE] = pvSigKs
        }
        clInMap[ConfigurationConstants.IS_BSP_COMPLIANT] = "false"

        getContext().registry.bind(id + "-server-cfg", new EsbServerConfigurer(
                inInterceptor: new WSS4JInInterceptor(svInMap),
                outInterceptor: new WSS4JOutInterceptor(svOutMap)))
        getContext().registry.bind(id + "-client-cfg", new EsbClientConfigurer(
                outInterceptor: new WSS4JOutInterceptor(clOutMap),
                inInterceptor: new WSS4JInInterceptor(clInMap)))

        from("cxf:${gw}?wsdlURL=${gwWsdl}&dataFormat=PAYLOAD&bus=#${id}-bus&cxfConfigurer=#${id}-server-cfg")
                .routeId(id)
                .process { exchange ->
                    def msg = exchange.getIn()
                    def apiKey = msg.getHeader("X-API-Key")
                    def clientId = apiKey ? acc.getClientIdByApiKey(apiKey as String) : null
                    if (clientId != null && !acc.hasAccess(clientId)) {
                        throw new SecurityException("Access denied for client: ${clientId}")
                    }
                    if (clientId != null && !acc.checkRateLimit(clientId)) {
                        throw new SecurityException("Rate limit exceeded for client: ${clientId}")
                    }
                    exchange.in.headers.remove("org.apache.cxf.headers.Header.list")
                }
                .to("cxf:${pv}?wsdlURL=${pvWsdl}&dataFormat=PAYLOAD&cxfConfigurer=#${id}-client-cfg")
                .process { exchange ->
                    exchange.in.headers.remove("org.apache.cxf.headers.Header.list")
                    // the provider leg's context (incl. its WS-Security results) must not reach
                    // the consumer's response, or "encrypt to the requester" picks the provider
                    exchange.in.headers.remove("ResponseContext")
                    exchange.in.headers.remove("RequestContext")
                }
    }
}
