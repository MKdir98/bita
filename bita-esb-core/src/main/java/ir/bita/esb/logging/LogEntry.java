package ir.bita.esb.logging;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Structured log entry for request processing.
 */
@Data
@Builder
public class LogEntry {
    
    // Request identification
    private String requestId;
    private String clientId;
    private String clientName;
    
    // Service/Route identification
    private Long serviceId;
    private String serviceName;
    private Long routeId;
    private String routeName;
    
    // Request details
    private String httpMethod;
    private String httpPath;
    private String contentType;
    private Integer contentLength;
    private String sourceIp;
    
    // Timing
    private Instant requestTime;
    private Instant responseTime;
    private Long durationMs;
    
    // Step tracking
    private String stepName;
    private Instant stepStartTime;
    private Long stepDurationMs;
    private String stepStatus; // SUCCESS, FAILED, SKIPPED
    
    // Response
    private Integer httpStatusCode;
    private String status; // SUCCESS, FAILED, TIMEOUT, RATE_LIMITED
    
    // Error details
    private String errorCode;
    private String errorMessage;
    private String errorStackTrace;
    
    // Custom metadata
    private Map<String, String> metadata;
}
