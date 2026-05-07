package ir.bita.common.camel;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Camel processor for structured request/response logging.
 */
@Slf4j
public class LoggingProcessor implements Processor {

    public static final String LOG_CONTEXT_KEY = "LogContext";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    
    private final LogPhase phase;
    private final String routeName;

    public enum LogPhase {
        REQUEST,
        RESPONSE,
        ERROR
    }

    public LoggingProcessor(LogPhase phase, String routeName) {
        this.phase = phase;
        this.routeName = routeName;
    }

    public static LoggingProcessor request(String routeName) {
        return new LoggingProcessor(LogPhase.REQUEST, routeName);
    }

    public static LoggingProcessor response(String routeName) {
        return new LoggingProcessor(LogPhase.RESPONSE, routeName);
    }

    public static LoggingProcessor error(String routeName) {
        return new LoggingProcessor(LogPhase.ERROR, routeName);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (phase) {
            case REQUEST -> processRequest(exchange);
            case RESPONSE -> processResponse(exchange);
            case ERROR -> processError(exchange);
        }
    }

    private void processRequest(Exchange exchange) {
        String requestId = exchange.getIn().getHeader(REQUEST_ID_HEADER, String.class);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            exchange.getIn().setHeader(REQUEST_ID_HEADER, requestId);
        }

        LogContext context = LogContext.builder()
                .requestId(requestId)
                .routeName(routeName)
                .startTime(Instant.now())
                .httpMethod(exchange.getIn().getHeader("CamelHttpMethod", String.class))
                .httpPath(exchange.getIn().getHeader("CamelHttpPath", String.class))
                .contentType(exchange.getIn().getHeader("Content-Type", String.class))
                .build();

        exchange.setProperty(LOG_CONTEXT_KEY, context);

        String bodyPreview = getBodyPreview(exchange);

        log.info("[{}] {} Request: {} {} - ContentType: {} - Body: {}",
                requestId,
                routeName,
                context.getHttpMethod(),
                context.getHttpPath(),
                context.getContentType(),
                bodyPreview);
    }

    private void processResponse(Exchange exchange) {
        LogContext context = exchange.getProperty(LOG_CONTEXT_KEY, LogContext.class);
        
        if (context == null) {
            log.warn("No log context found for response");
            return;
        }

        long duration = java.time.Duration.between(context.getStartTime(), Instant.now()).toMillis();
        String bodyPreview = getBodyPreview(exchange);

        log.info("[{}] {} Response: {}ms - Body: {}",
                context.getRequestId(),
                routeName,
                duration,
                bodyPreview);
    }

    private void processError(Exchange exchange) {
        LogContext context = exchange.getProperty(LOG_CONTEXT_KEY, LogContext.class);
        Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        
        String requestId = context != null ? context.getRequestId() : "unknown";
        long duration = context != null 
                ? java.time.Duration.between(context.getStartTime(), Instant.now()).toMillis()
                : 0;

        log.error("[{}] {} Error after {}ms: {}",
                requestId,
                routeName,
                duration,
                exception != null ? exception.getMessage() : "Unknown error",
                exception);
    }

    private String getBodyPreview(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return "<empty>";
        }
        
        String bodyStr = body.toString();
        int maxLength = 500;
        
        if (bodyStr.length() > maxLength) {
            return bodyStr.substring(0, maxLength) + "...";
        }
        return bodyStr;
    }

    @Data
    @Builder
    public static class LogContext {
        private String requestId;
        private String routeName;
        private Instant startTime;
        private String httpMethod;
        private String httpPath;
        private String contentType;
        private Map<String, Object> metadata;
    }
}
