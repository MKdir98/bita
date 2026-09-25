package ir.bita.system.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks the model a run uses. During development any model that currently answers with a real
 * tool call will do; for the measured run, {@code -Dbench.model=<id>} pins one model and the
 * probe only confirms it is reachable. Either way the whole run then uses that single model.
 */
public final class ModelProbe {

    private static final List<String> CANDIDATES = List.of(
            "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3.6-flash",
            "gpt-oss-120b", "deepseek-v4-flash", "qwen3.6-27b", "kimi-k2.6", "glm-5");

    private static final String TOOL_REQUEST = """
            {"model":"%s","messages":[{"role":"user","content":"What is the weather in Tehran? Use the tool."}],
             "tools":[{"type":"function","function":{"name":"get_weather","description":"get weather for a city",
               "parameters":{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}}}]}
            """;

    private static volatile String chosen;

    private ModelProbe() {
    }

    public static synchronized String model(String baseUrl, String apiKey) {
        if (chosen != null) {
            return chosen;
        }
        String pinned = System.getProperty("bench.model", System.getenv("BENCH_MODEL"));
        List<String> order = new ArrayList<>();
        if (pinned != null && !pinned.isBlank()) {
            order.add(pinned);
        } else {
            order.addAll(CANDIDATES);
        }
        List<String> failures = new ArrayList<>();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ObjectMapper json = new ObjectMapper();
        for (String model : order) {
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                                .timeout(Duration.ofSeconds(40))
                                .header("Authorization", "Bearer " + apiKey)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(TOOL_REQUEST.formatted(model)))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode toolCalls = json.readTree(resp.body()).path("choices").path(0).path("message").path("tool_calls");
                    if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                        chosen = model;
                        System.out.println("[bench] model for this run: " + model);
                        return model;
                    }
                    failures.add(model + ": answered without a tool call");
                } else {
                    failures.add(model + ": HTTP " + resp.statusCode());
                }
            } catch (Exception e) {
                failures.add(model + ": " + e.getMessage());
            }
        }
        throw new IllegalStateException("no model currently answers with a tool call: " + failures);
    }
}
