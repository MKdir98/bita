package ir.bita.esb.sync;

import groovy.lang.Binding;
import groovy.lang.Script;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import ir.bita.esb.access.RouteAccessService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs one version of a service's Groovy script. Vert.x closes every server and client the
 * script opened on this verticle's context when the verticle is undeployed — which is what lets
 * a reload actually retire a script that listens with Vert.x directly (e.g. rest-proxy).
 *
 * <p>Used for the service's live script ({@link SyncService}) and for a trial run of a proposed
 * template ({@link ScriptTrial}) — the same bindings in both, so a trial runs a script exactly as
 * a service would.
 */
@Slf4j
final class ScriptVerticle extends AbstractVerticle {

    private final Class<? extends Script> scriptClass;
    private final Map<String, Object> variableValues;
    private final RouteAccessService accessService;
    private final AtomicInteger inflight = new AtomicInteger();
    private CamelContext camelContext;
    private Router gatewayRouter;

    ScriptVerticle(Class<? extends Script> scriptClass, Map<String, Object> variableValues,
                   RouteAccessService accessService) {
        this.scriptClass = scriptClass;
        this.variableValues = variableValues;
        this.accessService = accessService;
    }

    Router gatewayRouter() {
        return gatewayRouter;
    }

    AtomicInteger inflight() {
        return inflight;
    }

    CamelContext camelContext() {
        return camelContext;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        try {
            Map<String, Object> bindings = new HashMap<>();
            if (variableValues != null) {
                bindings.putAll(variableValues);
            }
            gatewayRouter = Router.router(vertx);
            bindings.put("accessService", accessService);
            bindings.put("vertxInstance", vertx);
            bindings.put("gatewayRouter", gatewayRouter);

            Script instance = InvokerHelper.createScript(scriptClass, new Binding(bindings));
            RouteBuilder rb = (RouteBuilder) instance.run();

            camelContext = new DefaultCamelContext();
            camelContext.addRoutes(rb);
            camelContext.start();
            startPromise.complete();
        } catch (Exception e) {
            stopCamel();
            startPromise.fail(e);
        }
    }

    @Override
    public void stop() {
        stopCamel();
    }

    private void stopCamel() {
        if (camelContext != null) {
            try {
                camelContext.stop();
            } catch (Exception e) {
                log.warn("Error stopping Camel context", e);
            }
        }
    }
}
