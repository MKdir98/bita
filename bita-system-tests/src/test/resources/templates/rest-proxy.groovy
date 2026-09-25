import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.apache.camel.builder.RouteBuilder

/**
 * GroovyTemplate: REST-to-REST proxy.
 *
 * Variables (from service_groovy_config): pvAddress (provider base URL), gwPort, gwPath.
 * ESB bindings: vertxInstance, accessService, gatewayRouter.
 *
 * The ESB owns the gateway listener on gwPort; this script only registers its handlers on
 * gatewayRouter, so a new version can replace it without closing the port.
 *
 * Consumers identify themselves with the X-API-Key ESM issued them: no key or an unknown key →
 * 401, a key whose access to this service was never granted or was revoked → 403, over the
 * rate limit → 429 — none of these reach the provider.
 */

def vertx  = vertxInstance as Vertx
def router = gatewayRouter as Router
def pvUrl  = new URL(pvAddress as String)
def pvHost = pvUrl.host
def pvPort = pvUrl.port
def acc    = accessService

def webClient = WebClient.create(vertx, new WebClientOptions()
        .setConnectTimeout(5000)
        .setIdleTimeout(30))

router.route().handler(BodyHandler.create())

router.route("/*").handler { ctx ->
    def req      = ctx.request()
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

    def proxyReq = webClient.requestAbs(req.method(), "http://${pvHost}:${pvPort}${req.path()}")
    // the consumer's credential stays at the gateway; the provider learns who called from X-Client-Id
    req.headers().each { entry ->
        if (!entry.key.equalsIgnoreCase("X-API-Key") && !entry.key.equalsIgnoreCase("X-Client-Id")) {
            proxyReq.putHeader(entry.key, entry.value)
        }
    }
    proxyReq.putHeader("X-Client-Id", clientId)

    proxyReq.sendBuffer(ctx.body().buffer())
        .onSuccess { resp ->
            def response = ctx.response().setStatusCode(resp.statusCode())
            resp.headers().each { entry -> response.putHeader(entry.key, entry.value) }
            response.end(resp.body() ?: io.vertx.core.buffer.Buffer.buffer())
        }
        .onFailure { err ->
            ctx.response().setStatusCode(502).end("Bad Gateway: ${err.message}")
        }
}

new RouteBuilder() {
    void configure() {}
}
