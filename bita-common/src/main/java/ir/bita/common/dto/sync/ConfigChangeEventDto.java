package ir.bita.common.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO representing a configuration change event published to Kafka.
 * ESB pods consume these events to update their local caches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigChangeEventDto {

    /**
     * Unique identifier for this event.
     */
    private String eventId;

    /**
     * Type of the event.
     */
    private EventType eventType;

    /**
     * Timestamp when the event occurred.
     */
    private LocalDateTime timestamp;

    /**
     * ID of the affected entity.
     */
    private Long entityId;

    /**
     * Type of the affected entity.
     */
    private EntityType entityType;

    /**
     * Service ID affected by this change (for filtering by ESB pods).
     */
    private Long serviceId;

    /**
     * Additional event payload data.
     */
    private Map<String, Object> payload;

    /**
     * User who triggered the change.
     */
    private String triggeredBy;

    /**
     * Types of configuration change events.
     */
    public enum EventType {
        CREATED,
        UPDATED,
        DELETED,
        ACTIVATED,
        DEACTIVATED,
        ACCESS_GRANTED,
        ACCESS_REVOKED,
        ACCESS_EXPIRED,
        CREDENTIAL_ADDED,
        CREDENTIAL_REMOVED,
        PHASE_CHANGED,
        FULL_SYNC_REQUESTED
    }

    /**
     * Types of entities that can have configuration changes.
     */
    public enum EntityType {
        CLIENT,
        CREDENTIAL,
        SERVICE,
        SERVICE_COLLECTION,
        ROUTE,
        ENDPOINT,
        COMPONENT,
        SERVICE_ACCESS
    }
}
