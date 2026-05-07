package ir.bita.esm.llm.tool;

import ir.bita.esm.client.query.ClientQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool to list clients.
 */
@Component
@RequiredArgsConstructor
public class ListClientsTool implements LlmTool {

    private final ClientQueryService clientQueryService;

    @Override
    public String getName() {
        return "list_clients";
    }

    @Override
    public String getDescription() {
        return "لیست سازمان‌ها (کلاینت‌ها) را برمی‌گرداند. می‌توانید با نام یا تگ فیلتر کنید.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "search", Map.of(
                                "type", "string",
                                "description", "عبارت جستجو در نام سازمان"
                        ),
                        "page", Map.of(
                                "type", "integer",
                                "description", "شماره صفحه (از 0 شروع می‌شود)",
                                "default", 0
                        ),
                        "size", Map.of(
                                "type", "integer",
                                "description", "تعداد نتایج در هر صفحه",
                                "default", 10
                        )
                ),
                "required", new String[]{}
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        int page = arguments.containsKey("page") ? ((Number) arguments.get("page")).intValue() : 0;
        int size = arguments.containsKey("size") ? ((Number) arguments.get("size")).intValue() : 10;
        String search = (String) arguments.get("search");

        var result = (search != null && !search.isBlank())
                ? clientQueryService.searchClients(search, PageRequest.of(page, size))
                : clientQueryService.listClients(PageRequest.of(page, size));

        Map<String, Object> response = new HashMap<>();
        response.put("clients", result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("currentPage", result.getNumber());
        return response;
    }
}
