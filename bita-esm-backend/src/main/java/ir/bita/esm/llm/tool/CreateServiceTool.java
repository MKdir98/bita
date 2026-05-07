package ir.bita.esm.llm.tool;

import ir.bita.esm.service.command.CreateServiceCollectionCommand;
import ir.bita.esm.service.command.CreateServiceCommand;
import ir.bita.esm.service.handler.CreateServiceCollectionHandler;
import ir.bita.esm.service.handler.CreateServiceHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool to create a new service.
 */
@Component
@RequiredArgsConstructor
public class CreateServiceTool implements LlmTool {

    private final CreateServiceCollectionHandler collectionHandler;
    private final CreateServiceHandler serviceHandler;

    @Override
    public String getName() {
        return "create_service";
    }

    @Override
    public String getDescription() {
        return "یک سرویس جدید ایجاد می‌کند. اگر مجموعه (collection) وجود نداشته باشد، آن را هم ایجاد می‌کند.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "collectionId", Map.of(
                                "type", "integer",
                                "description", "شناسه مجموعه سرویس (اختیاری - اگر ندارید collectionName بدهید)"
                        ),
                        "collectionName", Map.of(
                                "type", "string",
                                "description", "نام مجموعه جدید (اگر collectionId ندارید)"
                        ),
                        "collectionBasePath", Map.of(
                                "type", "string",
                                "description", "مسیر پایه مجموعه (مثلاً /api/payment)"
                        ),
                        "serviceName", Map.of(
                                "type", "string",
                                "description", "نام سرویس"
                        ),
                        "serviceVersion", Map.of(
                                "type", "string",
                                "description", "نسخه سرویس (مثلاً v1)"
                        ),
                        "description", Map.of(
                                "type", "string",
                                "description", "توضیحات سرویس"
                        )
                ),
                "required", new String[]{"serviceName", "serviceVersion"}
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Long collectionId = arguments.containsKey("collectionId") 
                ? ((Number) arguments.get("collectionId")).longValue() : null;

        // Create collection if needed
        if (collectionId == null && arguments.containsKey("collectionName")) {
            var collection = collectionHandler.handle(CreateServiceCollectionCommand.builder()
                    .name((String) arguments.get("collectionName"))
                    .basePath((String) arguments.get("collectionBasePath"))
                    .description((String) arguments.get("description"))
                    .build());
            collectionId = collection.getId();
        }

        if (collectionId == null) {
            return Map.of("error", true, "message", "باید collectionId یا collectionName را مشخص کنید");
        }

        var service = serviceHandler.handle(CreateServiceCommand.builder()
                .collectionId(collectionId)
                .name((String) arguments.get("serviceName"))
                .version((String) arguments.get("serviceVersion"))
                .description((String) arguments.get("description"))
                .build());

        return Map.of(
                "success", true,
                "serviceId", service.getId(),
                "serviceName", service.getName(),
                "fullPath", service.getFullPath()
        );
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }
}
