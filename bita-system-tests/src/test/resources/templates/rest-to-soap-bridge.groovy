import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.apache.camel.builder.RouteBuilder

/**
 * GroovyTemplate: REST/JSON facade over a SOAP operation.
 *
 * Variables: pvAddress (provider SOAP endpoint), pvWsdlUri, soapOperation, gwPort, gwPath.
 * ESB bindings: vertxInstance, accessService, gatewayRouter.
 *
 * POST a flat JSON object → a SOAP 1.1 request for soapOperation whose child elements are the
 * JSON fields (in the WSDL's targetNamespace) → the provider's response element's children
 * returned as a flat JSON object. A SOAP Fault becomes HTTP 502 with the fault string.
 *
 * Consumers identify themselves with the X-API-Key ESM issued them: no key or an unknown key →
 * 401, a key whose access to this service was never granted or was revoked → 403, over the
 * rate limit → 429 — none of these reach the provider.
 */

def vertx  = vertxInstance as Vertx
def router = gatewayRouter as Router
def acc    = accessService
def pv     = pvAddress as String
def pvWsdl = pvWsdlUri as String
def op     = soapOperation as String

def webClient = WebClient.create(vertx, new WebClientOptions().setConnectTimeout(5000))
def targetNs = null

router.route().handler(BodyHandler.create())

router.post("/*").handler { ctx ->
    def apiKey   = ctx.request().getHeader("X-API-Key")
    def clientId = apiKey ? acc.getClientIdByApiKey(apiKey) : null
    if (clientId == null) {
        ctx.response().setStatusCode(401).end(apiKey ? "Unknown API key" : "API key required")
        return
    }
    if (!acc.hasAccess(clientId)) {
        ctx.response().setStatusCode(403).end("No access to this service")
        return
    }
    if (!acc.checkRateLimit(clientId)) {
        ctx.response().setStatusCode(429).end("Rate limit exceeded")
        return
    }
    def fields
    try {
        fields = new JsonSlurper().parseText(ctx.body().asString() ?: "{}") as Map
    } catch (Exception e) {
        ctx.response().setStatusCode(400).end("Body must be a JSON object")
        return
    }

    def nsReady = targetNs != null
            ? io.vertx.core.Future.succeededFuture(targetNs)
            : webClient.getAbs(pvWsdl).send().map { resp ->
                  targetNs = new XmlSlurper().parseText(resp.bodyAsString()).@targetNamespace.toString()
                  targetNs
              }

    nsReady.compose { ns ->
        def children = fields.collect { k, v -> "<${k}>${XmlUtil.escapeXml(String.valueOf(v))}</${k}>" }.join("")
        def envelope = """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ns="${ns}">""" +
                """<soapenv:Body><ns:${op}>${children}</ns:${op}></soapenv:Body></soapenv:Envelope>"""
        webClient.postAbs(pv)
                .putHeader("Content-Type", "text/xml; charset=utf-8")
                .putHeader("SOAPAction", "\"${op}\"")
                .sendBuffer(io.vertx.core.buffer.Buffer.buffer(envelope))
    }.onSuccess { resp ->
        def env = new XmlSlurper().parseText(resp.bodyAsString())
        def payload = env.Body.children()[0]
        if (payload.name() == "Fault") {
            ctx.response().setStatusCode(502).end("SOAP Fault: ${payload.faultstring.text()}")
            return
        }
        def json = payload.children().collectEntries { [(it.name()): it.text()] }
        ctx.response().putHeader("Content-Type", "application/json").end(JsonOutput.toJson(json))
    }.onFailure { err ->
        ctx.response().setStatusCode(502).end("Bad Gateway: ${err.message}")
    }
}

new RouteBuilder() {
    void configure() {}
}
