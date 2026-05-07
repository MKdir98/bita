package ir.bita.esm.llm.tool;

import ir.bita.esm.service.query.ServiceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool to list services.
 */
@Component
@RequiredArgsConstructor
public class ListServicesTool implements LlmTool {

    private final ServiceQueryService serviceQueryService;

    @Override
    public String getName() {
        return "list_services";
    }

    @Override
    public String getDescription() {
        return "لیست سرویس‌ها و مجموعه سرویس‌ها را برمی‌گرداند.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "collectionId", Map.of(
                                "type", "integer",
                                "description", "فیلتر بر اساس شناسه مجموعه"
                        ),
                        "page", Map.of(
                                "type", "integer",
                                "default", 0
                        ),
                        "size", Map.of(
                                "type", "integer",
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
        Long collectionId = arguments.containsKey("collectionId") 
                ? ((Number) arguments.get("collectionId")).longValue() : null;

        Map<String, Object> result = new HashMap<>();

        if (collectionId != null) {
            result.put("services", serviceQueryService.getServicesInCollection(collectionId));
        } else {
            var services = serviceQueryService.listServices(PageRequest.of(page, size));
            result.put("services", services.getContent());
            result.put("totalElements", services.getTotalElements());
            result.put("totalPages", services.getTotalPages());
        }

        // Also include collections
        var collections = serviceQueryService.listCollections(PageRequest.of(0, 20));
        result.put("collections", collections.getContent());

        return result;
    }
}
