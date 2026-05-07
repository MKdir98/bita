package ir.bita.esm.route.dto;

import ir.bita.common.domain.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {
    private Long id;
    private String name;
    private String description;
    private Long serviceId;
    private String serviceName;
    private String serviceVersion;
    private Long templateId;
    private String templateName;
    private EndpointResponse fromEndpoint;
    private EndpointResponse toEndpoint;
    private List<ComponentResponse> components;
    private List<FileResponse> files;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointResponse {
        private Long id;
        private String name;
        private String description;
        private String uri;
        private Integer defaultRateLimit;
        private Map<String, Object> config;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentResponse {
        private Long id;
        private String name;
        private String description;
        private String componentType;
        private String className;
        private int orderIndex;
        private Map<String, Object> config;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileResponse {
        private Long id;
        private String filename;
        private FileType fileType;
        private Long fileSize;
        private String mimeType;
        private LocalDateTime createdAt;
    }
}
