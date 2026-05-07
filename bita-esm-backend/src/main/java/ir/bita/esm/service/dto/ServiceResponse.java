package ir.bita.esm.service.dto;

import ir.bita.common.domain.ServicePhase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for ServiceEntity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponse {

    private Long id;
    private String name;
    private String version;
    private String description;
    private ServicePhase phase;
    private Long collectionId;
    private String collectionName;
    private String basePath;
    private String fullPath;
    private Integer minReplicas;
    private Integer maxReplicas;
    private Integer targetCpuPercent;
    private String k8sDeploymentName;
    private String k8sServiceName;
    private boolean deployed;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
