package ir.bita.esm.route.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteTemplateResponse {
    private Long id;
    private String name;
    private String description;
    private Long fromEndpointTemplateId;
    private String fromEndpointTemplateName;
    private Long toEndpointTemplateId;
    private String toEndpointTemplateName;
    private Map<String, Object> configSchema;
    private Map<String, Object> componentConfig;
    private String category;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
