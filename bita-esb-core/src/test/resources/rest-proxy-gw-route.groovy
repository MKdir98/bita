import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.apache.camel.builder.RouteBuilder

/**
 * REST-to-REST proxy route using pure Vert.x non-blocking I/O.
 *
 * Injected bindings:
 *   vertxInstance  - io.vertx.core.Vertx
 *   pvAddress      - String, e.g. "http://127.0.0.1:29450/api"
 *   gwPort         - int, gateway listen port
 *   accessService  - RouteAccessService
 */

def vertx    = vertxInstance as Vertx
def pvUrl    = new URL(pvAddress as String)
def pvHost   = pvUrl.host
def pvPort   = pvUrl.port
def pvPath   = pvUrl.path
def acc      = accessService
def port     = gwPort as int

def clientOpts = new WebClientOptions()
        .setConnectTimeout(5000)
        .setIdleTimeout(30)

def webClient = WebClient.create(vertx, clientOpts)

def router = Router.router(vertx)
router.route().handler(BodyHandler.create())

router.route("/*").handler { ctx ->
    def req      = ctx.request()
    def apiKey   = req.getHeader("X-API-Key")
    def clientId = apiKey ? acc.getClientIdByApiKey(apiKey) : null

    // Access control
    if (apiKey != null && clientId == null) {
        ctx.response().setStatusCode(401).end("Unknown API key")
        return
    }

    // Rate limit
    if (!acc.checkRateLimit(clientId ?: "anonymous")) {
        ctx.response().setStatusCode(429).end("Rate limit exceeded")
        return
    }

    // Non-blocking proxy via Vert.x WebClient
    def body    = ctx.body().buffer()
    def method  = req.method()
    def reqPath = req.path()

    def proxyReq = webClient.requestAbs(method, "http://${pvHost}:${pvPort}${reqPath}")
    req.headers().each { entry -> proxyReq.putHeader(entry.key, entry.value) }
    if (clientId) {
        proxyReq.putHeader("X-Client-Id", clientId)
    }

    proxyReq.sendBuffer(body)
        .onSuccess { resp ->
            def response = ctx.response().setStatusCode(resp.statusCode())
            resp.headers().each { entry -> response.putHeader(entry.key, entry.value) }
            response.end(resp.body())
        }
        .onFailure { err ->
            ctx.response().setStatusCode(502).end("Bad Gateway: ${err.message}")
        }
}

vertx.createHttpServer()
     .requestHandler(router)
     .listen(port)
     .result()

// Vert.x owns HTTP — return empty RouteBuilder for Camel context
new RouteBuilder() {
    void configure() {}
}
