package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a route is deleted.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RouteDeletedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "ROUTE_DELETED";
    public static final String TOPIC = "bita.routes";

    private Long routeId;
    private Long serviceId;
    private String name;
    private String reason;

    public RouteDeletedEvent(Long routeId, Long serviceId, String name,
                             String reason, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.routeId = routeId;
        this.serviceId = serviceId;
        this.name = name;
        this.reason = reason;
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
