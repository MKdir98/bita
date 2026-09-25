import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.apache.camel.builder.RouteBuilder

/**
 * GroovyTemplate: SOAP passthrough to a provider that requires HTTP Basic authentication.
 *
 * Variables: pvAddress, pvWsdlUri, gwPort, gwPath, backendUsername, backendPassword (SECRET).
 * ESB bindings: vertxInstance, accessService, gatewayRouter.
 *
 * Consumers call the gateway without the provider's credentials; the gateway adds them, so
 * the secret never leaves the ESB.
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
def basic  = "Basic " + "${backendUsername}:${backendPassword}".toString().bytes.encodeBase64().toString()

def webClient = WebClient.create(vertx, new WebClientOptions().setConnectTimeout(5000))

router.route().handler(BodyHandler.create())

router.get("/*").handler { ctx ->
    if (!ctx.request().params().contains("wsdl") && !ctx.request().query()?.equalsIgnoreCase("wsdl")) {
        ctx.next()
        return
    }
    def gwUrl = "http://${ctx.request().host()}${ctx.request().path()}"
    webClient.getAbs(pvWsdl).putHeader("Authorization", basic).send()
        .onSuccess { resp ->
            def wsdl = resp.bodyAsString().replaceAll(/(<(?:\w+:)?address\s+location=")[^"]*(")/, "\$1${gwUrl}\$2")
            ctx.response().putHeader("Content-Type", "text/xml; charset=utf-8").end(wsdl)
        }
        .onFailure { err -> ctx.response().setStatusCode(502).end("Bad Gateway: ${err.message}") }
}

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
    def req = webClient.postAbs(pv)
            .putHeader("Content-Type", ctx.request().getHeader("Content-Type") ?: "text/xml; charset=utf-8")
            .putHeader("Authorization", basic)
    def soapAction = ctx.request().getHeader("SOAPAction")
    if (soapAction != null) {
        req.putHeader("SOAPAction", soapAction)
    }
    req.sendBuffer(ctx.body().buffer())
        .onSuccess { resp ->
            ctx.response().setStatusCode(resp.statusCode())
                    .putHeader("Content-Type", resp.getHeader("Content-Type") ?: "text/xml; charset=utf-8")
                    .end(resp.body() ?: Buffer.buffer())
        }
        .onFailure { err -> ctx.response().setStatusCode(502).end("Bad Gateway: ${err.message}") }
}

new RouteBuilder() {
    void configure() {}
}
