package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;

/**
 * Event published when a route is updated.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RouteUpdatedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "ROUTE_UPDATED";
    public static final String TOPIC = "bita.routes";

    private Long routeId;
    private Long serviceId;
    private String name;
    private Set<String> changedFields;

    public RouteUpdatedEvent(Long routeId, Long serviceId, String name,
                             Set<String> changedFields, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.routeId = routeId;
        this.serviceId = serviceId;
        this.name = name;
        this.changedFields = changedFields;
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
        return String.valueOf(routeId);
    }
}
