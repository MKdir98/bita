package ir.bita.esm.route.dto;

import ir.bita.common.domain.VariableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GroovyTemplateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @NotBlank
    private String scriptText;

    private List<VariableMeta> variables = new ArrayList<>();

    @Data
    public static class VariableMeta {
        @NotBlank
        private String name;
        private String label;
        private String description;
        private VariableType type = VariableType.STRING;
        private boolean required = true;
        private String defaultValue;
        private String path;
    }
}
