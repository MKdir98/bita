package ir.bita.common.event;

import ir.bita.common.domain.ServicePhase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a new service is created.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ServiceCreatedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "SERVICE_CREATED";
    public static final String TOPIC = "bita.services";

    private Long serviceId;
    private Long collectionId;
    private String name;
    private String version;
    private ServicePhase phase;

    public ServiceCreatedEvent(Long serviceId, Long collectionId, String name,
                               String version, ServicePhase phase, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.serviceId = serviceId;
        this.collectionId = collectionId;
        this.name = name;
        this.version = version;
        this.phase = phase;
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
