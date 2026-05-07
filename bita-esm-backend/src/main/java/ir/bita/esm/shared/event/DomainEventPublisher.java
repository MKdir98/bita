package ir.bita.esm.shared.event;

import ir.bita.common.event.DomainEvent;
import ir.bita.esm.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for publishing domain events to Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a domain event to the appropriate Kafka topic.
     */
    public void publish(DomainEvent event) {
        String topic = getTopicForEvent(event);
        String key = event.getAggregateId() != null ? event.getAggregateId().toString() : null;

        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event {} to topic {}: {}", 
                                event.getClass().getSimpleName(), topic, ex.getMessage());
                    } else {
                        log.debug("Published event {} to topic {} with offset {}", 
                                event.getClass().getSimpleName(), topic, 
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private String getTopicForEvent(DomainEvent event) {
        String eventName = event.getClass().getSimpleName();
        
        if (eventName.startsWith("Client")) {
            return KafkaConfig.TOPIC_CLIENTS;
        } else if (eventName.startsWith("Credential")) {
            return KafkaConfig.TOPIC_CREDENTIALS;
        } else if (eventName.startsWith("Service") || eventName.startsWith("Access")) {
            return eventName.startsWith("Access") ? KafkaConfig.TOPIC_ACCESS : KafkaConfig.TOPIC_SERVICES;
        } else if (eventName.startsWith("Route")) {
            return KafkaConfig.TOPIC_ROUTES;
        }
        
        log.warn("Unknown event type {}, publishing to default topic", eventName);
        return "bita.events";
    }
}
