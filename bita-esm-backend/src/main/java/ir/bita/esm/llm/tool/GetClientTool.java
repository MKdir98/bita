package ir.bita.esm.llm.tool;

import ir.bita.esm.client.query.ClientQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool to get client details.
 */
@Component
@RequiredArgsConstructor
public class GetClientTool implements LlmTool {

    private final ClientQueryService clientQueryService;

    @Override
    public String getName() {
        return "get_client";
    }

    @Override
    public String getDescription() {
        return "اطلاعات کامل یک سازمان (کلاینت) را با شناسه برمی‌گرداند، شامل اعتبارنامه‌ها.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "clientId", Map.of(
                                "type", "integer",
                                "description", "شناسه سازمان"
                        )
                ),
                "required", new String[]{"clientId"}
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long clientId = ((Number) arguments.get("clientId")).longValue();
        var client = clientQueryService.getClient(clientId);

        return Map.of(
                "client", client,
                "credentials", client.getCredentials() != null ? client.getCredentials() : List.of()
        );
    }
}
