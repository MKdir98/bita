package ir.bita.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import ir.bita.esm.llm.entity.ToolExecution;
import ir.bita.esm.llm.entity.ToolExecutionStatus;
import ir.bita.esm.llm.tool.DataStoragePolicy;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.entity.ServiceGroovyConfig;
import ir.bita.system.support.BenchmarkBase;
import ir.bita.system.support.BenchmarkCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 — research question 1-b: when no template in the catalog fits, but the gateway can do what
 * is asked, does the model add a template that works — and is it then reused?
 *
 * <p>Split: c51–c55 are the development cases, the ones seen while this path was built; c56–c60
 * (the same capabilities, other organisations and values) are held out and run only in the final
 * measurement ({@code -Dbench.split=test}), whose numbers are the ones reported.
 *
 * <p>Cases: the L5 documents from llm-benchmark, each needing one capability no
 * catalog template has — a Bearer token added for the provider, a JSON-to-XML response, GET-only,
 * a fan-out over two providers, a response deadline — plus a second, similar request from another
 * organisation. Setup: the six catalog templates, the providers running. Action: the conversation;
 * the reviewer confirms every proposal (the human does not review the code here, so what is
 * measured is the model and the tool's checks). Expectation: the model does not configure the
 * service from a catalog template; it adds a template through groovy_template and configures the
 * service from it; a consumer calling through a real ESB gets exactly the asked-for behaviour, and
 * a call without an API key is refused (401); the second request is configured from the same new
 * template, with no new one, and behaves the same. The template's name and variable names are the
 * model's choice, so correctness is judged by behaviour, not by comparing values.
 */
@DisplayName("S3 [سؤال ۱-ب] ساخت قالب تازه وقتی قالبی جور نیست، و استفادهٔ دوباره از آن")
class TemplateCreationTest extends BenchmarkBase {

    record CreationCase(String id, String capability, BenchmarkCase first, BenchmarkCase second) {
        @Override
        public String toString() {
            return id + " " + capability;
        }
    }

    static List<CreationCase> cases() throws Exception {
        String only = System.getProperty("bench.cases", "");
        String split = System.getProperty("bench.split", "dev");
        ObjectMapper json = new ObjectMapper();
        List<CreationCase> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(BenchmarkCase.CASES_DIR)) {
            for (Path f : files.sorted().toList()) {
                JsonNode n = json.readTree(f.toFile());
                if (!"L5".equals(n.path("level").asText()) || !split.equals(n.path("split").asText())
                        || (!only.isBlank() && !List.of(only.split(",")).contains(n.path("id").asText()))) {
                    continue;
                }
                JsonNode e = n.path("expected");
                JsonNode r = n.path("reuse");
                out.add(new CreationCase(n.path("id").asText(), n.path("capability").asText(),
                        caseOf(n.path("id").asText(), e.path("serviceName").asText(), e.path("serviceVersion").asText(),
                                e.path("collectionName").asText(), e.path("variables"), n.path("document").asText()),
                        caseOf(n.path("id").asText() + "-reuse", r.path("serviceName").asText(), "v1",
                                r.path("collectionName").asText(), r.path("variables"), r.path("document").asText())));
            }
        }
        return out;
    }

    private static BenchmarkCase caseOf(String id, String svc, String ver, String org, JsonNode vars, String doc) {
        Map<String, String> v = new LinkedHashMap<>();
        vars.fields().forEachRemaining(x -> v.put(x.getKey(), x.getValue().asText()));
        return new BenchmarkCase(id, "L5", "create-template", null, "-", svc, ver, org, v, doc, null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void createsAWorkingTemplateAndReusesIt(CreationCase cc) throws Exception {
        Map<String, GroovyTemplate> catalog = seedCatalog();
        List<Long> catalogIds = catalog.values().stream().map(GroovyTemplate::getId).toList();
        Vertx vertx = Vertx.vertx();
        try {
            Providers p1 = startProviders(vertx, cc.capability(), cc.first().variables(), "first");
            Run first = converse(localised(cc.first(), p1), confirmAll, false);
            Outcome o1 = outcome(first, catalogIds);
            Check c1 = o1.config == null ? Check.fail("no service_groovy_config was executed")
                    : check(vertx, cc.capability(), first, o1.config, cc.first().variables(), p1);

            Outcome o2 = null;
            Check c2 = Check.fail("not attempted: the first request produced no template");
            Run second = null;
            if (o1.createdTemplateId != null) {
                Providers p2 = startProviders(vertx, cc.capability(), cc.second().variables(), "second");
                second = converse(localised(cc.second(), p2), confirmAll, false);
                o2 = outcome(second, catalogIds);
                c2 = o2.config == null ? Check.fail("no service_groovy_config was executed")
                        : check(vertx, cc.capability(), second, o2.config, cc.second().variables(), p2);
            }
            boolean reused = o2 != null && o2.createdTemplateId == null && o2.config != null
                    && o1.createdTemplateId.equals(o2.config.getGroovyTemplate().getId());

            report("S3", first, mapOf(
                    "split", System.getProperty("bench.split", "dev"),
                    "capability", cc.capability(),
                    "configuredFromCatalogTemplate", o1.fromCatalog,
                    "createdTemplate", o1.createdTemplateName,
                    "toolRejectedDrafts", o1.rejectedDrafts,
                    "unsafeTemplateRegistered", o1.unsafeRegistered,
                    "served", c1.ok, "detail", c1.detail,
                    "reuseCreatedAnother", o2 != null && o2.createdTemplateId != null,
                    "reused", reused, "reuseServed", c2.ok, "reuseDetail", c2.detail,
                    "reuseFormatRepairs", second == null ? null : second.formatRepairs,
                    "reuseEvents", second == null ? null : second.events));

            assertThat(o1.unsafeRegistered).as("a template breaking the storage policy was registered").isFalse();
            assertThat(o1.fromCatalog).as("configured from a catalog template that does not fit; events: %s", first.events)
                    .isFalse();
            assertThat(o1.createdTemplateId).as("no template was added; events: %s", first.events).isNotNull();
            assertThat(c1.ok).as("the new service's behaviour: %s", c1.detail).isTrue();
            assertThat(reused).as("the second request did not reuse the new template; events: %s",
                    second == null ? null : second.events).isTrue();
            assertThat(c2.ok).as("the reused service's behaviour: %s", c2.detail).isTrue();
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    // ------------------------------------------------------------------ what the model did

    private static final class Outcome {
        ServiceGroovyConfig config;
        boolean fromCatalog;
        Long createdTemplateId;
        String createdTemplateName;
        int rejectedDrafts;
        boolean unsafeRegistered;
    }

    private Outcome outcome(Run run, List<Long> catalogIds) {
        Outcome o = new Outcome();
        for (ToolExecution e : run.executed) {
            if (!"groovy_template".equals(e.getToolName())) {
                continue;
            }
            if (e.getStatus() == ToolExecutionStatus.EXECUTED && e.getResult() != null) {
                o.createdTemplateId = ((Number) e.getResult().get("templateId")).longValue();
                o.createdTemplateName = String.valueOf(e.getResult().get("name"));
                String script = templateRepository.findById(o.createdTemplateId).orElseThrow().getScriptText();
                o.unsafeRegistered |= !DataStoragePolicy.violations("scriptText", script).isEmpty();
            } else if (e.getStatus() == ToolExecutionStatus.FAILED) {
                o.rejectedDrafts++;
            }
        }
        o.config = configOf(run);
        o.fromCatalog = o.config != null && catalogIds.contains(o.config.getGroovyTemplate().getId());
        return o;
    }

    // ------------------------------------------------------------------ providers

    /** The stub provider(s) for a capability, and what they observed. */
    private record Providers(Map<String, Integer> ports, String markerA, String markerB,
                             AtomicInteger calls, AtomicReference<String> lastAuthorization,
                             AtomicReference<String> apiKeySeen, java.util.concurrent.atomic.AtomicBoolean slow) {
    }

    private Providers startProviders(Vertx vertx, String capability, Map<String, String> vars, String tag)
            throws Exception {
        String markerA = "pv-" + tag + "-a";
        String markerB = "pv-" + tag + "-b";
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        // the provider turns slow on demand, whatever path it is called on: the document only says
        // what to do when it is slow, not which path the gateway calls it with
        var slow = new java.util.concurrent.atomic.AtomicBoolean();
        Map<String, Integer> ports = new LinkedHashMap<>();
        int delay = vars.containsKey("timeoutMs") ? Integer.parseInt(vars.get("timeoutMs")) + 1500 : 0;
        String token = vars.get("bearerToken");

        for (String origin : origins(vars)) {
            String marker = ports.isEmpty() ? markerA : markerB;
            int port = freePort();
            vertx.createHttpServer().requestHandler(req -> {
                calls.incrementAndGet();
                auth.set(req.getHeader("Authorization"));
                if (req.getHeader("X-API-Key") != null) {
                    apiKey.set(req.getHeader("X-API-Key"));
                }
                if (token != null && !("Bearer " + token).equals(req.getHeader("Authorization"))) {
                    req.response().setStatusCode(401).end("provider: bearer token required");
                    return;
                }
                String body = new JsonObject().put("marker", marker).put("status", "ok").encode();
                Runnable answer = () -> req.response().putHeader("Content-Type", "application/json").end(body);
                if (delay > 0 && slow.get()) {
                    vertx.setTimer(delay, t -> answer.run());
                } else {
                    answer.run();
                }
            }).listen(port).toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            ports.put(origin, port);
        }
        return new Providers(ports, markerA, markerB, calls, auth, apiKey, slow);
    }

    private static final Pattern ORIGIN = Pattern.compile("https?://[^/\\s]+");

    private static List<String> origins(Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        for (String k : List.of("pvAddress", "pvAddressFirst", "pvAddressSecond")) {
            if (vars.containsKey(k)) {
                Matcher m = ORIGIN.matcher(vars.get(k));
                if (m.find() && !out.contains(m.group())) {
                    out.add(m.group());
                }
            }
        }
        return out;
    }

    /** The document with each provider origin it names pointed at the stub started for it. */
    private static BenchmarkCase localised(BenchmarkCase c, Providers p) {
        String doc = c.document();
        Map<String, String> vars = new LinkedHashMap<>(c.variables());
        for (var e : p.ports().entrySet()) {
            String local = "http://127.0.0.1:" + e.getValue();
            doc = doc.replace(e.getKey(), local);
            vars.replaceAll((k, v) -> v.replace(e.getKey(), local));
        }
        return new BenchmarkCase(c.id(), c.level(), c.expectedBehavior(), null, "-", c.serviceName(),
                c.serviceVersion(), c.collectionName(), vars, doc, null);
    }

    // ------------------------------------------------------------------ behaviour

    private record Check(boolean ok, String detail) {
        static Check fail(String why) {
            return new Check(false, why);
        }
    }

    private Check check(Vertx vertx, String capability, Run run, ServiceGroovyConfig config,
                        Map<String, String> vars, Providers p) throws Exception {
        String apiKey = grantApiKeyConsumer(run.serviceId, "consumer-" + run.c.id());
        try {
            startEsb(run.serviceId);
        } catch (Exception e) {
            return Check.fail("ESB did not start the template: " + e.getMessage());
        }
        Map<String, Object> applied = config.getVariableValues();
        int gwPort = Integer.parseInt(String.valueOf(applied.get("gwPort")));
        String gwPath = String.valueOf(applied.get("gwPath"));
        WebClient http = WebClient.create(vertx);
        List<String> notes = new ArrayList<>();

        HttpResponse<Buffer> noKey = send(http, HttpMethod.GET, gwPort, gwPath + "/ping", null, 5000);
        boolean guarded = noKey.statusCode() == 401 && p.calls().get() == 0;
        notes.add("noKey=" + noKey.statusCode());

        boolean ok;
        switch (capability) {
            case "bearer-token" -> {
                HttpResponse<Buffer> r = send(http, HttpMethod.GET, gwPort, gwPath + "/ping", apiKey, 5000);
                ok = r.statusCode() == 200 && body(r).contains(p.markerA())
                        && ("Bearer " + vars.get("bearerToken")).equals(p.lastAuthorization().get())
                        && p.apiKeySeen().get() == null;
                notes.add("GET=" + r.statusCode() + " auth=" + p.lastAuthorization().get()
                        + " keyForwarded=" + (p.apiKeySeen().get() != null) + " body=" + snippet(r));
            }
            case "json-to-xml" -> {
                HttpResponse<Buffer> r = send(http, HttpMethod.GET, gwPort, gwPath + "/ping", apiKey, 5000);
                String ct = String.valueOf(r.getHeader("Content-Type"));
                String b = body(r).replaceAll("\\s", "");
                ok = r.statusCode() == 200 && ct.contains("xml") && b.contains("<response>")
                        && b.contains("<marker>" + p.markerA() + "</marker>") && b.contains("<status>ok</status>");
                notes.add("GET=" + r.statusCode() + " type=" + ct + " body=" + snippet(r));
            }
            case "get-only" -> {
                HttpResponse<Buffer> get = send(http, HttpMethod.GET, gwPort, gwPath + "/ping", apiKey, 5000);
                int before = p.calls().get();
                HttpResponse<Buffer> post = send(http, HttpMethod.POST, gwPort, gwPath + "/ping", apiKey, 5000);
                HttpResponse<Buffer> delete = send(http, HttpMethod.DELETE, gwPort, gwPath + "/ping", apiKey, 5000);
                ok = get.statusCode() == 200 && body(get).contains(p.markerA())
                        && post.statusCode() == 405 && delete.statusCode() == 405 && p.calls().get() == before;
                notes.add("GET=" + get.statusCode() + " POST=" + post.statusCode() + " DELETE=" + delete.statusCode()
                        + " providerCallsForRefused=" + (p.calls().get() - before));
            }
            case "fan-out" -> {
                HttpResponse<Buffer> r = send(http, HttpMethod.GET, gwPort, gwPath + "/ping", apiKey, 5000);
                boolean combined = false;
                try {
                    JsonObject j = r.bodyAsJsonObject();
                    combined = p.markerA().equals(j.getJsonObject("first").getString("marker"))
                            && p.markerB().equals(j.getJsonObject("second").getString("marker"));
                } catch (Exception ignored) {
                    // not the combined JSON
                }
                ok = r.statusCode() == 200 && combined;
                notes.add("GET=" + r.statusCode() + " body=" + snippet(r));
            }
            case "timeout" -> {
                int limit = Integer.parseInt(vars.get("timeoutMs"));
                HttpResponse<Buffer> fast = send(http, HttpMethod.GET, gwPort, gwPath + "/ping", apiKey, limit + 5000);
                p.slow().set(true);
                long t0 = System.currentTimeMillis();
                HttpResponse<Buffer> slow = send(http, HttpMethod.GET, gwPort, gwPath + "/ping", apiKey, limit + 5000);
                long slowMs = System.currentTimeMillis() - t0;
                p.slow().set(false);
                ok = fast.statusCode() == 200 && body(fast).contains(p.markerA())
                        && slow.statusCode() == 504 && slowMs < limit + 1000;
                notes.add("fast=" + fast.statusCode() + " slow=" + slow.statusCode() + " in " + slowMs + "ms (limit "
                        + limit + ")");
            }
            default -> throw new IllegalArgumentException(capability);
        }
        return new Check(ok && guarded, String.join(" ", notes) + (guarded ? "" : " ACCESS-GUARD-MISSING"));
    }

    private static HttpResponse<Buffer> send(WebClient http, HttpMethod method, int port, String uri, String apiKey,
                                             int timeoutMs) throws Exception {
        var req = http.request(method, port, "127.0.0.1", uri).timeout(timeoutMs);
        if (apiKey != null) {
            req.putHeader("X-API-Key", apiKey);
        }
        return req.sendBuffer(Buffer.buffer("{}"))
                .toCompletionStage().toCompletableFuture().get(timeoutMs + 2000L, TimeUnit.MILLISECONDS);
    }

    private static String body(HttpResponse<Buffer> r) {
        return r.bodyAsString() == null ? "" : r.bodyAsString();
    }

    private static String snippet(HttpResponse<Buffer> r) {
        String b = body(r);
        return b.substring(0, Math.min(160, b.length()));
    }
}
