package ir.bita.common.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all domain events.
 * Events are published to Kafka for ESB synchronization.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        // Client events
        @JsonSubTypes.Type(value = ClientCreatedEvent.class, name = "CLIENT_CREATED"),
        @JsonSubTypes.Type(value = ClientUpdatedEvent.class, name = "CLIENT_UPDATED"),
        @JsonSubTypes.Type(value = ClientDeletedEvent.class, name = "CLIENT_DELETED"),
        // Credential events
        @JsonSubTypes.Type(value = CredentialAddedEvent.class, name = "CREDENTIAL_ADDED"),
        @JsonSubTypes.Type(value = CredentialRemovedEvent.class, name = "CREDENTIAL_REMOVED"),
        // Service events
        @JsonSubTypes.Type(value = ServiceCreatedEvent.class, name = "SERVICE_CREATED"),
        @JsonSubTypes.Type(value = ServicePhaseChangedEvent.class, name = "SERVICE_PHASE_CHANGED"),
        // Access events
        @JsonSubTypes.Type(value = AccessGrantedEvent.class, name = "ACCESS_GRANTED"),
        @JsonSubTypes.Type(value = AccessRevokedEvent.class, name = "ACCESS_REVOKED"),
        @JsonSubTypes.Type(value = AccessExpiredEvent.class, name = "ACCESS_EXPIRED"),
        // Route events
        @JsonSubTypes.Type(value = RouteCreatedEvent.class, name = "ROUTE_CREATED"),
        @JsonSubTypes.Type(value = RouteUpdatedEvent.class, name = "ROUTE_UPDATED"),
        @JsonSubTypes.Type(value = RouteDeletedEvent.class, name = "ROUTE_DELETED"),
        @JsonSubTypes.Type(value = RouteDeactivatedEvent.class, name = "ROUTE_DEACTIVATED")
})
public abstract class DomainEvent {

    /**
     * Unique identifier for this event.
     */
    private String eventId;

    /**
     * Type of the event (set automatically by Jackson).
     */
    private String eventType;

    /**
     * Timestamp when the event occurred.
     */
    private LocalDateTime timestamp;

    /**
     * User who triggered this event.
     */
    private String triggeredBy;

    /**
     * Correlation ID for tracing related events.
     */
    private String correlationId;

    /**
     * Creates a new event with generated ID and current timestamp.
     */
    protected DomainEvent(String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Creates a new event with generated ID and current timestamp.
     */
    protected DomainEvent(String eventType, String triggeredBy) {
        this(eventType);
        this.triggeredBy = triggeredBy;
    }

    /**
     * Returns the Kafka topic for this event type.
     */
    public abstract String getTopic();

    /**
     * Returns the partition key for Kafka (for ordering).
     */
    public abstract String getPartitionKey();

    /**
     * Returns the aggregate ID for this event.
     */
    public abstract String getAggregateId();
}
