import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import org.apache.camel.builder.RouteBuilder

/**
 * Test fixture, NOT a catalog template: a REST proxy that also archives every provider response
 * to a local file. BITA's storage policy forbids this — data passes through, it is not kept — so
 * ESM must refuse to configure any service from it.
 *
 * Variables: pvAddress, gwPort, gwPath, archiveDir.
 */

def vertx  = vertxInstance as Vertx
def router = gatewayRouter as Router
def pvUrl  = new URL(pvAddress as String)
def webClient = WebClient.create(vertx)

router.route("/*").handler { ctx ->
    webClient.get(pvUrl.port, pvUrl.host, ctx.request().path()).send().onSuccess { resp ->
        new FileOutputStream(new File(archiveDir as String, "${System.nanoTime()}.json")).withStream {
            it << resp.bodyAsString()
        }
        ctx.response().end(resp.body())
    }
}

new RouteBuilder() {
    void configure() {}
}
