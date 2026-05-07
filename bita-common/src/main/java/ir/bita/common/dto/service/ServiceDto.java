package ir.bita.common.dto.service;

import ir.bita.common.domain.ServicePhase;
import ir.bita.common.dto.route.RouteDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing a service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDto {

    private Long id;
    
    private Long collectionId;
    
    private String collectionName;
    
    private String name;
    
    private String version;
    
    private String description;
    
    private ServicePhase phase;
    
    /**
     * Minimum number of replicas for Kubernetes deployment.
     */
    private Integer minReplicas;
    
    /**
     * Maximum number of replicas for Kubernetes deployment.
     */
    private Integer maxReplicas;
    
    /**
     * Target CPU utilization percentage for HPA.
     */
    private Integer targetCpuPercent;
    
    /**
     * Kubernetes deployment name (set when phase is ACTIVE).
     */
    private String k8sDeploymentName;
    
    /**
     * Kubernetes service name (set when phase is ACTIVE).
     */
    private String k8sServiceName;
    
    private List<RouteDto> routes;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private String createdBy;
}
