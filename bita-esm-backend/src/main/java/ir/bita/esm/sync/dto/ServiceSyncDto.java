package ir.bita.esm.sync.dto;

import ir.bita.common.domain.ServicePhase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for service sync data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceSyncDto {
    private Long id;
    private String name;
    private String version;
    private ServicePhase phase;
    private String basePath;
    private String fullPath;
    private String k8sDeploymentName;
    private String k8sServiceName;
    private Integer minReplicas;
    private Integer maxReplicas;
    private boolean active;
    private List<RouteSyncDto> routes;
}
