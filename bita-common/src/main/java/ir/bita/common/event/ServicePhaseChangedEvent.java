package ir.bita.common.event;

import ir.bita.common.domain.ServicePhase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a service's phase changes.
 * This is a critical event as it may trigger K8s deployment/undeployment.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ServicePhaseChangedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "SERVICE_PHASE_CHANGED";
    public static final String TOPIC = "bita.services";

    private Long serviceId;
    private String name;
    private ServicePhase oldPhase;
    private ServicePhase newPhase;
    private String reason;

    /**
     * K8s deployment name (set when transitioning to ACTIVE).
     */
    private String k8sDeploymentName;

    public ServicePhaseChangedEvent(Long serviceId, String name, ServicePhase oldPhase,
                                    ServicePhase newPhase, String reason, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.serviceId = serviceId;
        this.name = name;
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
        this.reason = reason;
    }

    /**
     * Returns true if this phase change requires K8s deployment.
     */
    public boolean requiresDeployment() {
        return newPhase == ServicePhase.ACTIVE && oldPhase != ServicePhase.ACTIVE;
    }

    /**
     * Returns true if this phase change requires K8s undeployment.
     */
    public boolean requiresUndeployment() {
        return oldPhase == ServicePhase.ACTIVE && newPhase != ServicePhase.ACTIVE;
    }

    @Override
    public String getTopic() {
        return TOPIC;
    }

    @Override
    public String getPartitionKey() {
        return String.valueOf(serviceId);
    }

    @Override
    public String getAggregateId() {
        return String.valueOf(serviceId);
    }
}
