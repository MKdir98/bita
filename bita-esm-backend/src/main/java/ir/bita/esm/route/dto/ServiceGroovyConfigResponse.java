package ir.bita.esm.route.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ServiceGroovyConfigResponse {
    private Long id;
    private Long serviceId;
    private Long groovyTemplateId;
    private String groovyTemplateName;
    private Map<String, Object> variableValues;
    private String assembledScript;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
