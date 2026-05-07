package ir.bita.esb.config;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * ESB Core configuration loaded from environment variables.
 */
@Getter
@Builder
@Slf4j
public class EsbConfig {

    private final Long serviceId;
    private final int httpPort;
    
    // ESM Backend connection
    private final String esmBaseUrl;
    private final String esmApiKey;
    
    // Kafka configuration
    private final String kafkaBootstrapServers;
    private final String kafkaGroupId;
    
    // Redis configuration
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    
    // Elasticsearch configuration
    private final String elasticsearchUrl;
    
    public static EsbConfig load() {
        Long serviceId = getEnvAsLong("SERVICE_ID")
                .orElseThrow(() -> new IllegalStateException("SERVICE_ID environment variable is required"));

        return EsbConfig.builder()
                .serviceId(serviceId)
                .httpPort(getEnvAsInt("HTTP_PORT").orElse(8080))
                .esmBaseUrl(getEnv("ESM_BASE_URL").orElse("http://bita-esm-backend:8081"))
                .esmApiKey(getEnv("ESM_API_KEY").orElse(""))
                .kafkaBootstrapServers(getEnv("KAFKA_BOOTSTRAP_SERVERS").orElse("localhost:9092"))
                .kafkaGroupId(getEnv("KAFKA_GROUP_ID").orElse("esb-core-" + serviceId))
                .redisHost(getEnv("REDIS_HOST").orElse("localhost"))
                .redisPort(getEnvAsInt("REDIS_PORT").orElse(6379))
                .redisPassword(getEnv("REDIS_PASSWORD").orElse(""))
                .elasticsearchUrl(getEnv("ELASTICSEARCH_URL").orElse("http://localhost:9200"))
                .build();
    }

    private static Optional<String> getEnv(String key) {
        return Optional.ofNullable(System.getenv(key))
                .filter(s -> !s.isBlank());
    }

    private static Optional<Integer> getEnvAsInt(String key) {
        return getEnv(key).map(Integer::parseInt);
    }

    private static Optional<Long> getEnvAsLong(String key) {
        return getEnv(key).map(Long::parseLong);
    }
}
