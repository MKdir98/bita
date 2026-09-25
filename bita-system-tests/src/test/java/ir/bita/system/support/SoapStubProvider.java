package ir.bita.system.support;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal SOAP 1.1 provider standing in for an organisation's real service: serves a WSDL on
 * GET {@code ?wsdl} and answers POSTed envelopes by echoing the request element's children back
 * inside {@code <op>Response}, plus a {@code marker} element — so a test can tell the call
 * reached this provider and the payload survived the gateway. Optionally requires HTTP Basic.
 */
public final class SoapStubProvider implements AutoCloseable {

    public static final String NAMESPACE = "http://bita.ir/test/provider";
    private static final Pattern FIRST_BODY_ELEMENT =
            Pattern.compile("<(?:soapenv:|soap:)?Body[^>]*>\\s*<(?:(\\w+):)?(\\w+)[^>]*>(.*?)</(?:\\w+:)?\\2>",
                    Pattern.DOTALL);

    private final Vertx vertx = Vertx.vertx();
    private final HttpServer server;
    private final int port;
    private final String marker;
    private final String requiredBasicAuth;
    private final AtomicInteger calls = new AtomicInteger();

    public SoapStubProvider(int port, String marker, String username, String password) throws Exception {
        this.port = port;
        this.marker = marker;
        this.requiredBasicAuth = username == null ? null
                : "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        this.server = vertx.createHttpServer()
                .requestHandler(req -> req.body().onSuccess(body -> {
                    if (requiredBasicAuth != null && !requiredBasicAuth.equals(req.getHeader("Authorization"))) {
                        req.response().setStatusCode(401).end("provider requires Basic auth");
                        return;
                    }
                    if ("GET".equals(req.method().name())) {
                        req.response().putHeader("Content-Type", "text/xml; charset=utf-8").end(wsdl());
                        return;
                    }
                    calls.incrementAndGet();
                    req.response().putHeader("Content-Type", "text/xml; charset=utf-8")
                            .end(respond(body.toString()));
                }))
                .listen(port)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    public String endpoint() {
        return "http://127.0.0.1:" + port + "/ws/provider";
    }

    public String wsdlUri() {
        return endpoint() + "?wsdl";
    }

    public int calls() {
        return calls.get();
    }

    private String respond(String request) {
        Matcher m = FIRST_BODY_ELEMENT.matcher(request);
        if (!m.find()) {
            return envelope("<soapenv:Fault><faultcode>soapenv:Client</faultcode>"
                    + "<faultstring>no operation element in Body</faultstring></soapenv:Fault>");
        }
        String op = m.group(2);
        String children = m.group(3);
        return envelope("<ns:" + op + "Response xmlns:ns=\"" + NAMESPACE + "\">" + children
                + "<marker>" + marker + "</marker></ns:" + op + "Response>");
    }

    private static String envelope(String body) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Body>" + body + "</soapenv:Body></soapenv:Envelope>";
    }

    private String wsdl() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\""
                + " xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\""
                + " xmlns:tns=\"" + NAMESPACE + "\" targetNamespace=\"" + NAMESPACE + "\" name=\"Provider\">"
                + "<service name=\"ProviderService\"><port name=\"ProviderPort\" binding=\"tns:ProviderBinding\">"
                + "<soap:address location=\"" + endpoint() + "\"/></port></service></definitions>";
    }

    @Override
    public void close() throws Exception {
        server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
