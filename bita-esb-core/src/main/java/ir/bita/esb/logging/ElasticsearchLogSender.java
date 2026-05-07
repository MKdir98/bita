package ir.bita.esb.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esb.config.EsbConfig;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Log sender for Elasticsearch.
 */
@Slf4j
public class ElasticsearchLogSender implements LogSender {

    private static final String INDEX_PREFIX = "esb-logs-";
    
    private final String elasticsearchUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public ElasticsearchLogSender(EsbConfig config) {
        this.elasticsearchUrl = config.getElasticsearchUrl();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void send(LogEntry entry) {
        executor.submit(() -> {
            try {
                String indexName = INDEX_PREFIX + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                String url = elasticsearchUrl + "/" + indexName + "/_doc";
                
                String json = objectMapper.writeValueAsString(entry);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() >= 400) {
                    log.warn("Failed to send log to Elasticsearch: {} - {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Error sending log to Elasticsearch", e);
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
