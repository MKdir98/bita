package ir.bita.common.dto.route;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO representing a Camel endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointDto {

    private Long id;
    
    private Long templateId;
    
    private String templateName;
    
    private String name;
    
    private String description;
    
    /**
     * The resolved URI for this endpoint.
     * Example: "cxf:bean:PaymentService?wsdlURL=payment.wsdl"
     */
    private String uri;
    
    /**
     * Configuration values for this endpoint.
     */
    private Map<String, Object> config;
    
    /**
     * Default rate limit (requests per minute).
     * Can be overridden per client in service_access.
     */
    private Integer defaultRateLimit;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
