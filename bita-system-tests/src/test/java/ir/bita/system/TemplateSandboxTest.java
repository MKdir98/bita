package ir.bita.system;

import ir.bita.esm.llm.tool.ToolRegistry;
import ir.bita.system.support.SystemTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security — model-written templates run in the ESB sandbox.
 *
 * <p>Setup: ESM's real groovy_template tool and the sandbox ESB. Action: propose templates that
 * pass the tool's static checks (access guard, gateway variables, syntax, storage scan) but do
 * one dangerous thing in the request handler — run a process, read a file, reflect, load a
 * class, evaluate a string, reach the JVM or open a database connection, including forms that
 * slip past a string scan ({@code "jd" + "bc"}, {@code "ls".execute()}). Expectation: every one is
 * refused by the sandboxed trial run, with the reason, and nothing is registered; the same
 * template without the dangerous line is accepted.
 */
@DisplayName("امنیت: قالب نوشتهٔ مدل در محیط محدود گذرگاه اجرا می‌شود")
class TemplateSandboxTest extends SystemTestBase {

    @Autowired
    ToolRegistry tools;

    private static String template(String dangerousLine) {
        return """
                import io.vertx.core.Vertx
                import io.vertx.ext.web.Router
                import io.vertx.ext.web.client.WebClient
                import org.apache.camel.builder.RouteBuilder

                def vertx  = vertxInstance as Vertx
                def router = gatewayRouter as Router
                def acc    = accessService
                def web    = WebClient.create(vertx)

                router.route("/*").handler { ctx ->
                    def apiKey   = ctx.request().getHeader("X-API-Key")
                    def clientId = apiKey ? acc.getClientIdByApiKey(apiKey) : null
                    if (clientId == null) { ctx.response().setStatusCode(401).end("API key required"); return }
                    if (!acc.hasAccess(clientId)) { ctx.response().setStatusCode(403).end("No access"); return }
                    %s
                    web.getAbs(pvAddress as String).send()
                        .onSuccess { r -> ctx.response().end(r.bodyAsString() ?: "") }
                        .onFailure { e -> ctx.response().setStatusCode(502).end("Bad Gateway") }
                }

                new RouteBuilder() { void configure() {} }
                """.formatted(dangerousLine);
    }

    private Map<String, Object> propose(String name, String script) {
        return tools.executeTool("groovy_template", Map.of(
                "name", name + "-" + System.nanoTime(),
                "description", "sandbox check",
                "variables", List.of(
                        Map.of("name", "pvAddress", "type", "STRING", "required", true),
                        Map.of("name", "gwPort", "type", "PORT", "required", true),
                        Map.of("name", "gwPath", "type", "STRING", "required", true)),
                "scriptText", script));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "run a process            | Runtime.getRuntime().exec('id')",
            "run a process (GDK)      | 'id'.execute()",
            "process builder          | new ProcessBuilder('id').start()",
            "read a file              | def secret = new File('/etc/hosts').text",
            "write a file (nio)       | java.nio.file.Files.writeString(java.nio.file.Path.of('/tmp/x'), 'y')",
            "load a class             | Class.forName('java.lang.Runtime')",
            "reflect via .class       | def c = ctx.class.forName('java.lang.Runtime')",
            "evaluate a string        | evaluate('1 + 1')",
            "new shell                | new GroovyShell().evaluate('1')",
            "reach the JVM            | System.exit(0)",
            "read the environment     | def env = System.getenv()",
            "database, split string   | java.sql.DriverManager.getConnection('jd' + 'bc:h2:mem:x')",
    })
    void dangerousTemplateIsRefused(String what, String line) {
        Map<String, Object> result = propose("sandbox", template(line));

        String message = String.valueOf(result.get("message"));
        assertThat(result.get("error")).as("%s was accepted: %s", what, result).isEqualTo(true);
        // refused by the sandboxed trial run, or already by the storage scan in front of it
        assertThat(message).as(what).containsAnyOf("اجرای آزمایشی", "سیاست امنیتی");
        String reason = message.lines().filter(l -> l.contains("not allowed") || l.contains("«")).findFirst()
                .orElse(message.lines().findFirst().orElse(""));
        System.out.printf("[SANDBOX] %s refused by %s: %s%n", what,
                message.contains("اجرای آزمایشی") ? "sandbox" : "storage-scan", reason);
        // the refusal must be about the dangerous line, not something every script contains
        assertThat(message).as(what).doesNotContain("runScript");
    }

    @Test
    @DisplayName("the same template without the dangerous line is accepted")
    void benignTemplateIsAccepted() {
        Map<String, Object> result = propose("benign", template("// nothing dangerous"));
        assertThat(result.get("error")).as("result: %s", result).isNotEqualTo(true);
        assertThat(result.get("templateId")).isNotNull();
    }
}
