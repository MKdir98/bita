package ir.bita.esb.logging;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates log entries from SEDA channel and sends to log senders.
 */
@Slf4j
public class LogAggregator implements Processor {

    private final Map<String, LogEntryBuilder> inProgressLogs = new ConcurrentHashMap<>();
    private final List<LogSender> senders = new ArrayList<>();

    public void addSender(LogSender sender) {
        senders.add(sender);
    }

    /**
     * Start tracking a new request.
     */
    public void startRequest(String requestId, String clientId, Long routeId) {
        LogEntry.LogEntryBuilder builder = LogEntry.builder()
                .requestId(requestId)
                .clientId(clientId)
                .routeId(routeId)
                .requestTime(Instant.now())
                .status("IN_PROGRESS");

        inProgressLogs.put(requestId, new LogEntryBuilder(builder));
        log.trace("Started tracking request: {}", requestId);
    }

    /**
     * Record a step in the processing pipeline.
     */
    public void recordStep(String requestId, String stepName, Instant startTime, long durationMs, String status) {
        LogEntryBuilder builder = inProgressLogs.get(requestId);
        if (builder != null) {
            // Create step log entry
            LogEntry stepLog = builder.getBuilder()
                    .stepName(stepName)
                    .stepStartTime(startTime)
                    .stepDurationMs(durationMs)
                    .stepStatus(status)
                    .build();

            // Send step log asynchronously
            sendLog(stepLog);
        }
    }

    /**
     * Complete a request and send final log.
     */
    public void completeRequest(String requestId, int statusCode, String status, String errorMessage) {
        LogEntryBuilder builder = inProgressLogs.remove(requestId);
        if (builder != null) {
            Instant now = Instant.now();
            long duration = java.time.Duration.between(builder.getStartTime(), now).toMillis();

            LogEntry finalLog = builder.getBuilder()
                    .responseTime(now)
                    .durationMs(duration)
                    .httpStatusCode(statusCode)
                    .status(status)
                    .errorMessage(errorMessage)
                    .build();

            sendLog(finalLog);
            log.trace("Completed tracking request: {} ({}ms, {})", requestId, duration, status);
        }
    }

    /**
     * Add metadata to a request.
     */
    public void addMetadata(String requestId, String key, String value) {
        LogEntryBuilder builder = inProgressLogs.get(requestId);
        if (builder != null) {
            builder.addMetadata(key, value);
        }
    }

    /**
     * Set request details.
     */
    public void setRequestDetails(String requestId, String method, String path, String sourceIp) {
        LogEntryBuilder builder = inProgressLogs.get(requestId);
        if (builder != null) {
            builder.getBuilder()
                    .httpMethod(method)
                    .httpPath(path)
                    .sourceIp(sourceIp);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Process SEDA messages
        Object body = exchange.getIn().getBody();
        if (body instanceof LogEntry logEntry) {
            sendLog(logEntry);
        }
    }

    private void sendLog(LogEntry entry) {
        for (LogSender sender : senders) {
            try {
                sender.send(entry);
            } catch (Exception e) {
                log.error("Failed to send log via {}", sender.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Internal builder wrapper to track state.
     */
    private static class LogEntryBuilder {
        private final LogEntry.LogEntryBuilder builder;
        private final Instant startTime;
        private final Map<String, String> metadata = new ConcurrentHashMap<>();

        LogEntryBuilder(LogEntry.LogEntryBuilder builder) {
            this.builder = builder;
            this.startTime = Instant.now();
        }

        LogEntry.LogEntryBuilder getBuilder() {
            builder.metadata(metadata);
            return builder;
        }

        Instant getStartTime() {
            return startTime;
        }

        void addMetadata(String key, String value) {
            metadata.put(key, value);
        }
    }
}
