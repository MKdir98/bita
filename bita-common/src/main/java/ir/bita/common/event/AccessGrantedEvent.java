package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event published when a client is granted access to a service.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccessGrantedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "ACCESS_GRANTED";
    public static final String TOPIC = "bita.access";

    private Long accessId;
    private Long clientId;
    private String clientName;
    private Long serviceId;
    private String serviceName;
    private Integer customRateLimit;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String reason;

    public AccessGrantedEvent(Long accessId, Long clientId, String clientName, Long serviceId,
                              String serviceName, Integer customRateLimit, LocalDateTime validFrom,
                              LocalDateTime validUntil, String reason, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.accessId = accessId;
        this.clientId = clientId;
        this.clientName = clientName;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.customRateLimit = customRateLimit;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
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
