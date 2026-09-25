package ir.bita.esm.route.dto;

import ir.bita.common.domain.VariableType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GroovyTemplateResponse {
    private Long id;
    private String name;
    private String description;
    private String scriptText;
    private List<VariableMeta> variables;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class VariableMeta {
        private String name;
        private String label;
        private String description;
        private VariableType type;
        private boolean required;
        private String defaultValue;
        private String path;
    }
}
