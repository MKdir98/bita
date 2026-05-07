package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a route is deactivated (but not deleted).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RouteDeactivatedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "ROUTE_DEACTIVATED";
    public static final String TOPIC = "bita.routes";

    private Long routeId;
    private Long serviceId;
    private String routeName;
    private String reason;

    public RouteDeactivatedEvent(Long routeId, Long serviceId, String routeName,
                                 String reason, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.routeId = routeId;
        this.serviceId = serviceId;
        this.routeName = routeName;
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
