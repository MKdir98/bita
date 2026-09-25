package ir.bita.system.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One benchmark case from {@code llm-benchmark/data/cases}: a Persian service-request document
 * and its ground truth, generated ground-truth-first so the expected answer is never ambiguous.
 */
public record BenchmarkCase(String id, String level, String expectedBehavior, String missingVariable,
                            String templateName, String serviceName, String serviceVersion,
                            String collectionName, Map<String, String> variables,
                            String document, String followUp) {

    public static final Path CASES_DIR = Path.of(System.getProperty("user.dir"))
            .resolveSibling("llm-benchmark").resolve("data").resolve("cases");

    public static List<BenchmarkCase> load(String... levels) throws IOException {
        List<String> wanted = List.of(levels);
        // -Dbench.cases=c01,c25 runs just those (for development); the measured run uses all
        String only = System.getProperty("bench.cases", "");
        List<String> onlyIds = only.isBlank() ? List.of() : List.of(only.split(","));
        ObjectMapper json = new ObjectMapper();
        List<BenchmarkCase> cases = new ArrayList<>();
        try (Stream<Path> files = Files.list(CASES_DIR)) {
            for (Path f : files.filter(p -> p.getFileName().toString().matches("c\\d+\\.json"))
                    .sorted(Comparator.naturalOrder()).toList()) {
                JsonNode n = json.readTree(f.toFile());
                if (!wanted.contains(n.path("level").asText())
                        || (!onlyIds.isEmpty() && !onlyIds.contains(n.path("id").asText()))) {
                    continue;
                }
                JsonNode e = n.path("expected");
                Map<String, String> vars = new LinkedHashMap<>();
                e.path("variables").fields().forEachRemaining(v -> vars.put(v.getKey(), v.getValue().asText()));
                cases.add(new BenchmarkCase(
                        n.path("id").asText(), n.path("level").asText(), n.path("expectedBehavior").asText(),
                        n.hasNonNull("missingVariable") ? n.get("missingVariable").asText() : null,
                        e.path("templateName").asText(), e.path("serviceName").asText(),
                        e.path("serviceVersion").asText(), e.path("collectionName").asText(),
                        vars, n.path("document").asText(),
                        n.hasNonNull("followUp") ? n.get("followUp").asText() : null));
            }
        }
        return cases;
    }

    /** One case by id, regardless of any -Dbench.cases filter (for tests built on a specific document). */
    public static BenchmarkCase byId(String id) throws IOException {
        String saved = System.getProperty("bench.cases");
        try {
            System.clearProperty("bench.cases");
            return load("L1", "L2", "L3", "L4").stream().filter(c -> c.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("no benchmark case " + id));
        } finally {
            if (saved != null) {
                System.setProperty("bench.cases", saved);
            }
        }
    }

    /** The provider origin the document names, e.g. {@code http://10.21.1.17:8080}. */
    public String providerOrigin() {
        String pv = variables.get("pvAddress");
        int schemeEnd = pv.indexOf("://") + 3;
        int pathStart = pv.indexOf('/', schemeEnd);
        return pathStart < 0 ? pv : pv.substring(0, pathStart);
    }

    /**
     * The same case with its unreachable provider origin replaced by a reachable one, in the
     * document, the follow-up and the ground truth alike — nothing else in the text changes,
     * including decoy addresses in noisy documents.
     */
    public BenchmarkCase withProviderOrigin(String origin) {
        String from = providerOrigin();
        Map<String, String> vars = new LinkedHashMap<>();
        variables.forEach((k, v) -> vars.put(k, v.replace(from, origin)));
        return new BenchmarkCase(id, level, expectedBehavior, missingVariable, templateName, serviceName,
                serviceVersion, collectionName, vars, document.replace(from, origin),
                followUp == null ? null : followUp.replace(from, origin));
    }

    @Override
    public String toString() {
        return id + " [" + level + "] " + templateName + " → " + expectedBehavior;
    }
}
