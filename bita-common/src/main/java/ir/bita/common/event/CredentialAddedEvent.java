package ir.bita.common.event;

import ir.bita.common.domain.CredentialType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a credential is added to a client.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CredentialAddedEvent extends DomainEvent {

    public static final String EVENT_TYPE = "CREDENTIAL_ADDED";
    public static final String TOPIC = "bita.credentials";

    private Long credentialId;
    private Long clientId;
    private CredentialType credentialType;
    /**
     * Masked or partial value for logging purposes.
     */
    private String maskedValue;

    public CredentialAddedEvent(Long credentialId, Long clientId, CredentialType credentialType,
                                String maskedValue, String triggeredBy) {
        super(EVENT_TYPE, triggeredBy);
        this.credentialId = credentialId;
        this.clientId = clientId;
        this.credentialType = credentialType;
        this.maskedValue = maskedValue;
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
