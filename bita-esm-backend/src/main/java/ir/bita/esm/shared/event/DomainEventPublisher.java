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

        // Publishing is a side effect of an already-committed business decision: an unreachable
        // broker must not roll back the service/access change itself. KafkaProducer.send() can
        // throw synchronously (e.g. metadata timeout), not only fail the returned future.
        java.util.concurrent.CompletableFuture<org.springframework.kafka.support.SendResult<String, Object>> sent;
        try {
            sent = kafkaTemplate.send(topic, key, event);
        } catch (RuntimeException e) {
            log.error("Failed to publish event {} to topic {}: {}",
                    event.getClass().getSimpleName(), topic, e.getMessage());
            return;
        }
        sent
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
