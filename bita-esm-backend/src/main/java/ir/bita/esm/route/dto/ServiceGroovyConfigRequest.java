package ir.bita.esm.route.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ServiceGroovyConfigRequest {

    @NotNull
    private Long groovyTemplateId;

    private Map<String, Object> variableValues = new HashMap<>();
}
