package ir.bita.esm.llm.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esm.llm.dto.ActionResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmResponseValidatorTest {

    private final LlmResponseValidator validator = new LlmResponseValidator(new ObjectMapper());

    @Test
    void shouldValidateFencedJsonResponse() throws ValidationException {
        String fenced = """
                ```json
                {
                  "action": "endpoint_template",
                  "params": {
                    "name": "http-basic-auth-template",
                    "camelYaml": "- from:\\n    uri: \\"http://{{host}}:{{port}}/{{path}}\\"\\n    steps: []"
                  }
                }
                ```
                """;

        ActionResponse response = validator.validate(fenced);
        assertEquals("endpoint_template", response.getAction());
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
