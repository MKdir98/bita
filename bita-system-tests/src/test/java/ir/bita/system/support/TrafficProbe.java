package ir.bita.system.support;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A consumer calling a service's gateway continuously (one request at a time, back to back),
 * recording every outcome with a timestamp: which provider answered (its marker), or how the
 * call failed. Used to observe a live change from the outside, exactly as a consumer would.
 */
public final class TrafficProbe implements AutoCloseable {

    /** One call: when it completed, the provider marker it reached (null if it failed), and why not. */
    public record Sample(long atMs, String marker, int status, String failure) {
        public boolean ok() {
            return marker != null && status == 200;
        }
    }

    private final Vertx vertx = Vertx.vertx();
    private final WebClient http = WebClient.create(vertx, new WebClientOptions().setKeepAlive(true));
    private final List<Sample> samples = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final int port;
    private final String path;
    private final boolean soap;
    private final String apiKey;
    private static final java.util.regex.Pattern SOAP_MARKER = java.util.regex.Pattern.compile("<marker>([^<]*)</marker>");

    /** A REST consumer (GET, JSON answer). */
    public TrafficProbe(int port, String path, String apiKey) {
        this(port, path, false, apiKey);
    }

    /** {@code soap}: a SOAP consumer posting an envelope instead of a GET. */
    public TrafficProbe(int port, String path, boolean soap, String apiKey) {
        this.port = port;
        this.path = path;
        this.soap = soap;
        this.apiKey = apiKey;
        next();
    }

    private void next() {
        if (!running.get()) {
            return;
        }
        var call = soap
                ? http.post(port, "127.0.0.1", path).timeout(5000).putHeader("X-API-Key", apiKey)
                        .putHeader("Content-Type", "text/xml; charset=utf-8")
                        .sendBuffer(io.vertx.core.buffer.Buffer.buffer(
                                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>"
                                        + "<ns:Ping xmlns:ns=\"" + SoapStubProvider.NAMESPACE + "\"><n>1</n></ns:Ping>"
                                        + "</soapenv:Body></soapenv:Envelope>"))
                : http.get(port, "127.0.0.1", path).timeout(5000).putHeader("X-API-Key", apiKey).send();
        call
                .onSuccess(resp -> {
                    String marker = null;
                    try {
                        if (soap) {
                            var m = SOAP_MARKER.matcher(resp.bodyAsString());
                            marker = m.find() ? m.group(1) : null;
                        } else {
                            marker = resp.bodyAsJsonObject().getString("marker");
                        }
                    } catch (Exception ignored) {
                        // no marker in the body (e.g. a 502 text) — a failed call
                    }
                    samples.add(new Sample(System.currentTimeMillis(), marker, resp.statusCode(),
                            resp.statusCode() == 200 ? null : resp.statusCode() + " " + resp.bodyAsString()));
                })
                .onFailure(err -> samples.add(new Sample(System.currentTimeMillis(), null, 0, err.toString())))
                .onComplete(ignored -> vertx.runOnContext(v -> next()));
    }

    public List<Sample> samples() {
        return List.copyOf(samples);
    }

    public List<Sample> since(long fromMs) {
        return samples.stream().filter(s -> s.atMs() >= fromMs).toList();
    }

    /** Waits until a call after {@code fromMs} reaches {@code marker}; returns when it completed. */
    public long awaitMarker(String marker, long fromMs, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Sample s : samples) {
                if (s.atMs() >= fromMs && marker.equals(s.marker())) {
                    return s.atMs();
                }
            }
            Thread.sleep(2);
        }
        throw new AssertionError("no call reached " + marker + " within " + timeoutMs + "ms");
    }

    @Override
    public void close() throws Exception {
        running.set(false);
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
