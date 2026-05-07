package ir.bita.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;

/**
 * Event published when a client is updated.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClientUpdatedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "CLIENT_UPDATED";
    public static final String TOPIC = "bita.clients";

    private Long clientId;
    private String name;
    private Set<String> changedFields;

    public ClientUpdatedEvent(Long clientId, String name, Set<String> changedFields, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.clientId = clientId;
        this.name = name;
        this.changedFields = changedFields;
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
