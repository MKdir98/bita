package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a client is deleted (soft delete).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClientDeletedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "CLIENT_DELETED";
    public static final String TOPIC = "bita.clients";

    private Long clientId;
    private String name;
    private String reason;

    public ClientDeletedEvent(Long clientId, String name, String reason, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.clientId = clientId;
        this.name = name;
        this.reason = reason;
    }

    @Override
    public String getTopic() {
        return TOPIC;
    }

    @Override
    public String getPartitionKey() {
        return String.valueOf(clientId);
    }

    @Override
    public String getAggregateId() {
        return String.valueOf(clientId);
    }
}
