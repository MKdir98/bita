package ir.bita.esm.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka configuration and topic definitions.
 */
@Configuration
public class KafkaConfig {

    public static final String TOPIC_CLIENTS = "bita.clients";
    public static final String TOPIC_CREDENTIALS = "bita.credentials";
    public static final String TOPIC_SERVICES = "bita.services";
    public static final String TOPIC_ACCESS = "bita.access";
    public static final String TOPIC_ROUTES = "bita.routes";

    @Bean
    public NewTopic clientsTopic() {
        return TopicBuilder.name(TOPIC_CLIENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic credentialsTopic() {
        return TopicBuilder.name(TOPIC_CREDENTIALS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic servicesTopic() {
        return TopicBuilder.name(TOPIC_SERVICES)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic accessTopic() {
        return TopicBuilder.name(TOPIC_ACCESS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic routesTopic() {
        return TopicBuilder.name(TOPIC_ROUTES)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
