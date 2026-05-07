package ir.bita.esm.route.dto;

import ir.bita.common.domain.ComponentType;
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
public class ComponentTemplateResponse {
    private Long id;
    private String name;
    private String description;
    private ComponentType componentType;
    private String className;
    private Map<String, Object> configSchema;
    private String category;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
