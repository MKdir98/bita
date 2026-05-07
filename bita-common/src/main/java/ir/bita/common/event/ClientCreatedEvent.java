package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a new client is created.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClientCreatedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "CLIENT_CREATED";
    public static final String TOPIC = "bita.clients";

    private Long clientId;
    private String name;

    public ClientCreatedEvent(Long clientId, String name, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.clientId = clientId;
        this.name = name;
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
