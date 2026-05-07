package ir.bita.common.event;

import ir.bita.common.domain.CredentialType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a credential is removed from a client.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CredentialRemovedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "CREDENTIAL_REMOVED";
    public static final String TOPIC = "bita.credentials";

    private Long credentialId;
    private Long clientId;
    private CredentialType credentialType;
    private String reason;

    public CredentialRemovedEvent(Long credentialId, Long clientId, CredentialType credentialType,
                                  String reason, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.credentialId = credentialId;
        this.clientId = clientId;
        this.credentialType = credentialType;
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
        return String.valueOf(credentialId);
    }
}
