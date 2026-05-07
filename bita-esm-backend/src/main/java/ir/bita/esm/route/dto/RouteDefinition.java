package ir.bita.esm.route.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Route definition DTO for ESB core consumption.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteDefinition {

    private Long routeId;
    private String routeName;
    private Long serviceId;
    private String serviceVersion;
    private String servicePath;
    
    private EndpointDef fromEndpoint;
    private EndpointDef toEndpoint;
    private List<ComponentDef> components;
    private List<FileDef> files;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointDef {
        private Long id;
        private String name;
        private String uri;
        private Integer rateLimit;
        private Map<String, Object> config;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentDef {
        private Long id;
        private String name;
        private String componentType;
        private String className;
        private int orderIndex;
        private Map<String, Object> config;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileDef {
        private Long id;
        private String filename;
        private String fileType;
        private String storagePath;
        private String contentHash;
        private String content; // For inline small files
    }
}
