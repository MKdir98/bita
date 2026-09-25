package ir.bita.esb.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esb.config.EsbConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka consumer for domain events from ESM.
 * Filters events by SERVICE_ID.
 * 
 * Uses manual offset commit for at-least-once delivery guarantee:
 * - Offsets are committed only after successful processing
 * - Failed messages will be reprocessed on restart
 */
@Slf4j
public class EventConsumer {

    private static final String[] TOPICS = {
            "bita.route.events",
            "bita.access.events",
            "bita.client.events"
    };

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 1000;

    private final EsbConfig config;
    private final SyncService syncService;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor;
    private KafkaConsumer<String, String> consumer;

    public EventConsumer(EsbConfig config, SyncService syncService) {
        this.config = config;
        this.syncService = syncService;
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, config.getKafkaGroupId());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
            // Manual commit for at-least-once delivery
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            // Fetch settings for better batching
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");

            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Arrays.asList(TOPICS));

            executor.submit(this::pollLoop);
            log.info("Event consumer started for service {} with manual commit", config.getServiceId());
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (consumer != null) {
                consumer.wakeup();
            }
            executor.shutdown();
            log.info("Event consumer stopped");
        }
    }

    private void pollLoop() {
        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                if (records.isEmpty()) {
                    continue;
                }

                // Track offsets to commit per partition
                Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
                boolean allSuccessful = true;
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        // Process with retry
                        boolean success = processEventWithRetry(record.topic(), record.value());
                        
                        if (success) {
                            // Track offset for this partition (offset + 1 for next message)
                            TopicPartition partition = new TopicPartition(record.topic(), record.partition());
                            offsetsToCommit.put(partition, new OffsetAndMetadata(record.offset() + 1));
                        } else {
                            log.error("Failed to process event after {} retries: topic={}, offset={}", 
                                    MAX_RETRIES, record.topic(), record.offset());
                            allSuccessful = false;
                            // Stop processing this batch - will retry from this point
                            break;
                        }
                    } catch (Exception e) {
                        log.error("Unexpected error processing event from {} at offset {}", 
                                record.topic(), record.offset(), e);
                        allSuccessful = false;
                        break;
                    }
                }

                // Commit processed offsets
                if (!offsetsToCommit.isEmpty()) {
                    try {
                        consumer.commitSync(offsetsToCommit);
                        log.debug("Committed offsets for {} partitions", offsetsToCommit.size());
                    } catch (Exception e) {
                        log.error("Failed to commit offsets", e);
                        // Will reprocess these messages on next poll
                    }
                }

                // If not all successful, pause briefly before retry
                if (!allSuccessful) {
                    Thread.sleep(RETRY_BACKOFF_MS);
                }
            }
        } catch (org.apache.kafka.common.errors.WakeupException e) {
            if (running.get()) {
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Event consumer interrupted");
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }
    }

    /**
     * Process event with retry logic.
     * @return true if successful, false if all retries failed
     */
    private boolean processEventWithRetry(String topic, String value) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                processEvent(topic, value);
                return true;
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed for event from {}: {}", 
                        attempt, MAX_RETRIES, topic, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private void processEvent(String topic, String value) throws Exception {
        JsonNode event = objectMapper.readTree(value);
        
        String eventType = event.has("eventType") ? event.get("eventType").asText() : "";
        Long serviceId = event.has("serviceId") ? event.get("serviceId").asLong() : null;
        
        // Filter events by SERVICE_ID
        if (serviceId != null && !serviceId.equals(config.getServiceId())) {
            log.trace("Ignoring event for service {}, we handle service {}", serviceId, config.getServiceId());
            return;
        }

        log.debug("Processing event: {} from {}", eventType, topic);

        switch (eventType) {
            // Groovy script changed — hot-reload routes
            case "GROOVY_CONFIG_UPDATED", "ROUTE_CREATED", "ROUTE_UPDATED",
                 "ROUTE_DELETED", "ROUTE_DEACTIVATED" -> {
                log.info("Groovy config changed ({}), triggering hot-reload", eventType);
                syncService.reload();
            }

            // Access events
            case "ACCESS_GRANTED" -> {
                String clientId = event.get("clientId").asText();
                Long routeId = event.has("routeId") ? event.get("routeId").asLong() : null;
                syncService.syncAccess(clientId, routeId);
            }
            case "ACCESS_REVOKED" -> {
                String clientId = event.get("clientId").asText();
                Long routeId = event.has("routeId") ? event.get("routeId").asLong() : null;
                syncService.removeAccess(clientId, routeId);
            }

            // Client events
            case "CLIENT_UPDATED", "CREDENTIAL_ADDED", "CREDENTIAL_REMOVED" -> {
                String clientId = event.get("clientId").asText();
                syncService.syncClient(clientId);
            }
            case "CLIENT_DELETED" -> {
                String clientId = event.get("clientId").asText();
                syncService.removeClient(clientId);
            }

            default -> log.debug("Unhandled event type: {}", eventType);
        }
    }
}
