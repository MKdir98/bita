package ir.bita.esm.service.entity;

import ir.bita.common.domain.ServicePhase;
import ir.bita.esm.route.entity.Route;
import ir.bita.esm.shared.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

/**
 * Service entity representing a deployed service version.
 * Named ServiceEntity to avoid conflict with Java's Service interface.
 */
@Entity
@Table(name = "service", indexes = {
        @Index(name = "idx_service_collection", columnList = "collection_id"),
        @Index(name = "idx_service_phase", columnList = "phase"),
        @Index(name = "idx_service_version", columnList = "service_version")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_service_collection_version", columnNames = {"collection_id", "service_version"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class ServiceEntity extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private ServiceCollection collection;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Semantic version (e.g., "1.0", "1.1", "2.0")
     */
    @Column(name = "service_version", nullable = false, length = 20)
    private String serviceVersion;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 20)
    @Builder.Default
    private ServicePhase phase = ServicePhase.DRAFT;

    /**
     * Minimum number of replicas for K8s deployment.
     */
    @Column(name = "min_replicas", nullable = false)
    @Builder.Default
    private Integer minReplicas = 2;

    /**
     * Maximum number of replicas for K8s deployment.
     */
    @Column(name = "max_replicas", nullable = false)
    @Builder.Default
    private Integer maxReplicas = 10;

    /**
     * Target CPU utilization percentage for HPA.
     */
    @Column(name = "target_cpu_percent", nullable = false)
    @Builder.Default
    private Integer targetCpuPercent = 80;

    /**
     * K8s Deployment name (set when phase is ACTIVE).
     */
    @Column(name = "k8s_deployment_name", length = 100)
    private String k8sDeploymentName;

    /**
     * K8s Service name (set when phase is ACTIVE).
     */
    @Column(name = "k8s_service_name", length = 100)
    private String k8sServiceName;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Route> routes = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ServiceAccess> accessRules = new ArrayList<>();

    /**
     * Adds a route to this service.
     */
    public void addRoute(Route route) {
        routes.add(route);
        route.setService(this);
    }

    /**
     * Removes a route from this service.
     */
    public void removeRoute(Route route) {
        routes.remove(route);
        route.setService(null);
    }

    /**
     * Checks if this service is deployed to Kubernetes.
     */
    public boolean isDeployed() {
        return phase != null && phase.isDeployed() && k8sDeploymentName != null;
    }

    /**
     * Gets the full path for this service.
     */
    public String getFullPath() {
        return collection != null ? collection.getFullPath(serviceVersion) : null;
    }

    /**
     * Gets ingress-routable path considering phase.
     * TEST phase uses /test prefix to keep it isolated from production routes.
     */
    public String getRoutablePath() {
        String fullPath = getFullPath();
        if (fullPath == null) {
            return null;
        }
        if (phase == ServicePhase.TEST) {
            return "/test" + fullPath;
        }
        return fullPath;
    }

    /**
     * Generates K8s resource names based on collection and version.
     */
    public String generateK8sName() {
        if (collection == null) return null;
        String collectionName = collection.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
        String versionName = serviceVersion.replace(".", "-");
        return collectionName + "-" + versionName + "-esb";
    }
}
