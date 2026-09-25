package ir.bita.esm.llm.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esm.llm.dto.ActionResponse;
import ir.bita.esm.llm.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmResponseValidatorTest {

    // valid actions are sourced live from ToolRegistry.getToolNames() (see
    // LlmResponseValidator) rather than a second, independently-maintained list — that
    // second list was exactly the bug GroovyLlmScenarioLiveTest found and this fixed:
    // it still held pre-Groovy-migration names like "endpoint_template" and was missing
    // "create_service"/"service_groovy_config" entirely, so a live model correctly
    // calling create_service silently failed validation. Stubbing the real tool names
    // here keeps this unit test honest about what's actually registered.
    private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    private final LlmResponseValidator validator = new LlmResponseValidator(new ObjectMapper(), toolRegistry);

    @Test
    void shouldValidateFencedJsonResponse() throws ValidationException {
        when(toolRegistry.getToolNames()).thenReturn(List.of("component_template", "complete"));

        String fenced = """
                ```json
                {
                  "action": "component_template",
                  "params": {
                    "name": "http-basic-auth-template",
                    "camelYaml": "- from:\\n    uri: \\"http://{{host}}:{{port}}/{{path}}\\"\\n    steps: []"
                  }
                }
                ```
                """;

        ActionResponse response = validator.validate(fenced);
        assertEquals("component_template", response.getAction());
        assertEquals("http-basic-auth-template", response.getParams().get("name"));
    }

    @Test
    void shouldExtractJsonWhenSurroundedByText() {
        String mixed = """
                sure, here is the action:
                {
                  "action": "complete",
                  "params": {
                    "summary": "done"
                  }
                }
                thank you
                """;

        String extracted = validator.extractJsonObject(mixed);
        assertNotNull(extracted);
        assertTrue(extracted.startsWith("{"));
        assertTrue(extracted.endsWith("}"));
    }
}
