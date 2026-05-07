package ir.bita.esb.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esb.config.EsbConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * Log sender for Kafka (analytics).
 */
@Slf4j
public class KafkaLogSender implements LogSender {

    private static final String TOPIC = "esb-request-logs";
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public KafkaLogSender(EsbConfig config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "10");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");

        this.producer = new KafkaProducer<>(props);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public void send(LogEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, entry.getRequestId(), json);
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Failed to send log to Kafka", exception);
                }
            });
        } catch (Exception e) {
            log.error("Error sending log to Kafka", e);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}
