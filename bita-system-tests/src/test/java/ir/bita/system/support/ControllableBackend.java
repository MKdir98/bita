package ir.bita.system.support;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A provider backend whose behaviour a test switches at runtime: healthy (200 + marker),
 * failing with 500 or 429, or down altogether (listener closed, then reopened on the same port).
 */
public final class ControllableBackend implements AutoCloseable {

    public enum Mode { OK, ERROR_500, ERROR_429 }

    private final Vertx vertx = Vertx.vertx();
    private final int port;
    private final String marker;
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.OK);
    private final AtomicInteger served = new AtomicInteger();
    private volatile HttpServer server;

    public ControllableBackend(int port, String marker) throws Exception {
        this.port = port;
        this.marker = marker;
        up();
    }

    public void mode(Mode m) {
        mode.set(m);
    }

    public int served() {
        return served.get();
    }

    public synchronized void down() throws Exception {
        if (server != null) {
            server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            server = null;
        }
    }

    public synchronized void up() throws Exception {
        if (server != null) {
            return;
        }
        server = vertx.createHttpServer()
                .requestHandler(req -> {
                    served.incrementAndGet();
                    switch (mode.get()) {
                        case ERROR_500 -> req.response().setStatusCode(500).end("provider error");
                        case ERROR_429 -> req.response().setStatusCode(429).putHeader("Retry-After", "1").end("slow down");
                        default -> req.response().putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("marker", marker).put("path", req.path()).encode());
                    }
                })
                .listen(port)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
