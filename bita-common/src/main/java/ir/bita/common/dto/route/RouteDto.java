package ir.bita.common.dto.route;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing a Camel route.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteDto {

    private Long id;
    
    private Long serviceId;
    
    private String name;
    
    private String description;
    
    /**
     * The from endpoint for this route.
     */
    private EndpointDto fromEndpoint;
    
    /**
     * The to endpoint for this route (destination).
     */
    private EndpointDto toEndpoint;
    
    /**
     * Components (processors/beans) in the route.
     */
    private List<ComponentDto> components;
    
    /**
     * Files attached to this route (WSDL, XSD, etc.).
     */
    private List<RouteFileDto> files;
    
    /**
     * Whether the route is active.
     */
    private boolean active;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
