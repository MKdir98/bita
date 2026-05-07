package ir.bita.common.camel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;

/**
 * Camel processor for standardized error handling.
 */
@Slf4j
public class ErrorHandlerProcessor implements Processor {

    public static final String ERROR_RESPONSE_HEADER = "ErrorResponse";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorResponseFormat format;
    private final boolean includeStackTrace;

    public enum ErrorResponseFormat {
        JSON,
        XML,
        SOAP_FAULT
    }

    public ErrorHandlerProcessor() {
        this(ErrorResponseFormat.JSON, false);
    }

    public ErrorHandlerProcessor(ErrorResponseFormat format, boolean includeStackTrace) {
        this.format = format;
        this.includeStackTrace = includeStackTrace;
    }

    public static ErrorHandlerProcessor json() {
        return new ErrorHandlerProcessor(ErrorResponseFormat.JSON, false);
    }

    public static ErrorHandlerProcessor jsonWithStackTrace() {
        return new ErrorHandlerProcessor(ErrorResponseFormat.JSON, true);
    }

    public static ErrorHandlerProcessor xml() {
        return new ErrorHandlerProcessor(ErrorResponseFormat.XML, false);
    }

    public static ErrorHandlerProcessor soapFault() {
        return new ErrorHandlerProcessor(ErrorResponseFormat.SOAP_FAULT, false);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        
        if (exception == null) {
            exception = exchange.getException();
        }

        if (exception == null) {
            log.warn("ErrorHandlerProcessor invoked but no exception found");
            return;
        }

        // Get request context
        String requestId = exchange.getIn().getHeader(LoggingProcessor.REQUEST_ID_HEADER, String.class);
        LoggingProcessor.LogContext logContext = exchange.getProperty(
                LoggingProcessor.LOG_CONTEXT_KEY, LoggingProcessor.LogContext.class);

        // Build error response
        ErrorResponse errorResponse = buildErrorResponse(exception, requestId, logContext);
        
        // Log the error
        log.error("[{}] Error in route: {}", requestId, exception.getMessage(), exception);

        // Format and set response
        String responseBody = formatErrorResponse(errorResponse);
        
        exchange.getIn().setHeader(ERROR_RESPONSE_HEADER, errorResponse);
        exchange.getIn().setBody(responseBody);
        
        // Set appropriate HTTP status code
        int statusCode = determineStatusCode(exception);
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
        
        // Set content type
        String contentType = switch (format) {
            case JSON -> "application/json";
            case XML -> "application/xml";
            case SOAP_FAULT -> "text/xml";
        };
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, contentType);
    }

    private ErrorResponse buildErrorResponse(Exception exception, String requestId, 
                                              LoggingProcessor.LogContext context) {
        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .requestId(requestId)
                .errorCode(determineErrorCode(exception))
                .message(exception.getMessage())
                .exceptionType(exception.getClass().getSimpleName());

        if (context != null) {
            builder.path(context.getHttpPath())
                   .method(context.getHttpMethod());
        }

        if (includeStackTrace) {
            builder.stackTrace(getStackTrace(exception));
        }

        // Handle specific exception types
        if (exception instanceof ValidationProcessor.ValidationException) {
            builder.errorCode("VALIDATION_ERROR");
        } else if (exception instanceof SecurityException) {
            builder.errorCode("SECURITY_ERROR");
        } else if (exception instanceof IllegalArgumentException) {
            builder.errorCode("BAD_REQUEST");
        }

        return builder.build();
    }

    private String determineErrorCode(Exception exception) {
        String className = exception.getClass().getSimpleName();
        return switch (className) {
            case "ValidationException" -> "VALIDATION_ERROR";
            case "SecurityException" -> "SECURITY_ERROR";
            case "IllegalArgumentException" -> "BAD_REQUEST";
            case "IllegalStateException" -> "INVALID_STATE";
            case "TimeoutException" -> "TIMEOUT";
            case "ConnectException" -> "CONNECTION_ERROR";
            default -> "INTERNAL_ERROR";
        };
    }

    private int determineStatusCode(Exception exception) {
        String className = exception.getClass().getSimpleName();
        return switch (className) {
            case "ValidationException", "IllegalArgumentException" -> 400;
            case "SecurityException" -> 403;
            case "NotFoundException" -> 404;
            case "TimeoutException" -> 504;
            default -> 500;
        };
    }

    private String formatErrorResponse(ErrorResponse error) throws Exception {
        return switch (format) {
            case JSON -> objectMapper.writeValueAsString(error);
            case XML -> formatAsXml(error);
            case SOAP_FAULT -> formatAsSoapFault(error);
        };
    }

    private String formatAsXml(ErrorResponse error) {
        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <error>
                    <timestamp>%s</timestamp>
                    <requestId>%s</requestId>
                    <errorCode>%s</errorCode>
                    <message><![CDATA[%s]]></message>
                    <path>%s</path>
                </error>
                """,
                error.getTimestamp(),
                error.getRequestId(),
                error.getErrorCode(),
                error.getMessage(),
                error.getPath() != null ? error.getPath() : "");
    }

    private String formatAsSoapFault(ErrorResponse error) {
        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body>
                        <soap:Fault>
                            <faultcode>soap:Server</faultcode>
                            <faultstring><![CDATA[%s]]></faultstring>
                            <detail>
                                <errorCode>%s</errorCode>
                                <requestId>%s</requestId>
                                <timestamp>%s</timestamp>
                            </detail>
                        </soap:Fault>
                    </soap:Body>
                </soap:Envelope>
                """,
                error.getMessage(),
                error.getErrorCode(),
                error.getRequestId(),
                error.getTimestamp());
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @Data
    @Builder
    public static class ErrorResponse {
        private String timestamp;
        private String requestId;
        private String errorCode;
        private String message;
        private String exceptionType;
        private String path;
        private String method;
        private String stackTrace;
        private Map<String, Object> details;
    }
}
