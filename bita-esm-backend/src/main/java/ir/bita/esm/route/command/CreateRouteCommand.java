package ir.bita.esm.route.command;

import ir.bita.common.domain.ComponentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Command for creating a route.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRouteCommand {

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    private Long templateId;

    @NotBlank(message = "Route name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotNull(message = "From endpoint is required")
    private EndpointConfig fromEndpoint;

    @NotNull(message = "To endpoint is required")
    private EndpointConfig toEndpoint;

    private List<ComponentConfig> components;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointConfig {
        private Long templateId;

        @NotBlank(message = "Endpoint name is required")
        private String name;

        private String description;

        @NotBlank(message = "URI is required")
        private String uri;

        private Map<String, Object> config;

        private Integer defaultRateLimit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentConfig {
        private Long templateId;

        @NotBlank(message = "Component name is required")
        private String name;

        private String description;

        @NotNull(message = "Component type is required")
        private ComponentType componentType;

        @NotBlank(message = "Class name is required")
        private String className;

        private Map<String, Object> config;
    }
}
