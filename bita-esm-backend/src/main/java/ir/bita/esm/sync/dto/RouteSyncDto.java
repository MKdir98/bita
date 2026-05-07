package ir.bita.esm.sync.dto;

import ir.bita.common.domain.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for route sync data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteSyncDto {
    private Long id;
    private String name;
    private Long serviceId;
    private boolean active;
    private EndpointSyncDto fromEndpoint;
    private EndpointSyncDto toEndpoint;
    private List<ComponentSyncDto> components;
    private List<FileSyncDto> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointSyncDto {
        private Long id;
        private String name;
        private String uri;
        private Integer defaultRateLimit;
        private Map<String, Object> config;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentSyncDto {
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
    public static class FileSyncDto {
        private Long id;
        private String filename;
        private FileType fileType;
        private String storagePath;
        private String contentHash;
        private String content;
    }
}
