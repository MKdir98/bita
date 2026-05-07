package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a new route is created.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RouteCreatedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "ROUTE_CREATED";
    public static final String TOPIC = "bita.routes";

    private Long routeId;
    private Long serviceId;
    private String name;
    private String fromEndpointUri;
    private String toEndpointUri;

    public RouteCreatedEvent(Long routeId, Long serviceId, String name,
                             String fromEndpointUri, String toEndpointUri, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.routeId = routeId;
        this.serviceId = serviceId;
        this.name = name;
        this.fromEndpointUri = fromEndpointUri;
        this.toEndpointUri = toEndpointUri;
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
