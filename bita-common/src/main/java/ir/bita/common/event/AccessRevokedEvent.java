package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a client's access to a service is revoked.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccessRevokedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "ACCESS_REVOKED";
    public static final String TOPIC = "bita.access";

    private Long accessId;
    private Long clientId;
    private String clientName;
    private Long serviceId;
    private String serviceName;
    private String reason;

    public AccessRevokedEvent(Long accessId, Long clientId, String clientName, Long serviceId,
                              String serviceName, String reason, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.accessId = accessId;
        this.clientId = clientId;
        this.clientName = clientName;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
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
        return String.valueOf(accessId);
    }
}
