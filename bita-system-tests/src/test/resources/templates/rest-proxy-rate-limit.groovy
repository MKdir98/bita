import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.apache.camel.builder.RouteBuilder

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * GroovyTemplate: REST-to-REST proxy with a per-consumer request cap.
 *
 * Variables: pvAddress, gwPort, gwPath, rateLimitPerMinute.
 * ESB bindings: vertxInstance, accessService, gatewayRouter.
 *
 * Each consumer (identified by its API key) gets rateLimitPerMinute requests per
 * fixed one-minute window; the next one in the same window is answered 429 without reaching
 * the provider.
 *
 * Consumers identify themselves with the X-API-Key ESM issued them: no key or an unknown key →
 * 401, a key whose access to this service was never granted or was revoked → 403, over the
 * rate limit → 429 — none of these reach the provider.
 */

def vertx  = vertxInstance as Vertx
def router = gatewayRouter as Router
def pvUrl  = new URL(pvAddress as String)
def limit  = Integer.parseInt(String.valueOf(rateLimitPerMinute))
def acc    = accessService

def webClient = WebClient.create(vertx, new WebClientOptions().setConnectTimeout(5000))
def windows = new ConcurrentHashMap<String, Map>()

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

    def consumer = clientId
    def minute   = (long) (System.currentTimeMillis() / 60_000L)
    def window   = windows.compute(consumer) { k, w ->
        (w == null || w.minute != minute) ? [minute: minute, count: new AtomicInteger()] : w
    }
    if (window.count.incrementAndGet() > limit) {
        ctx.response().setStatusCode(429).putHeader("Retry-After", "60").end("Rate limit exceeded")
        return
    }

    def proxyReq = webClient.requestAbs(req.method(), "http://${pvUrl.host}:${pvUrl.port}${req.path()}")
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
            response.end(resp.body() ?: Buffer.buffer())
        }
        .onFailure { err -> ctx.response().setStatusCode(502).end("Bad Gateway: ${err.message}") }
}

new RouteBuilder() {
    void configure() {}
}
