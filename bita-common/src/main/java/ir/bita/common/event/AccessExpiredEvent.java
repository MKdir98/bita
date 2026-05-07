package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event published when a client's access to a service expires.
 * This event is triggered by the access expiration scheduler.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccessExpiredEvent extends DomainEvent {

    public static final String EVENT_TYPE = "ACCESS_EXPIRED";
    public static final String TOPIC = "bita.access";

    private Long accessId;
    private Long clientId;
    private String clientName;
    private Long serviceId;
    private String serviceName;
    private LocalDateTime expiredAt;

    public AccessExpiredEvent(Long accessId, Long clientId, String clientName, Long serviceId,
                              String serviceName, LocalDateTime expiredAt) {
        super(EVENT_TYPE, "SYSTEM");
        this.accessId = accessId;
        this.clientId = clientId;
        this.clientName = clientName;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.expiredAt = expiredAt;
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
