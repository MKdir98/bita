package ir.bita.esb.route;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Route definition loaded from ESM.
 */
@Data
@Builder
public class RouteDefinition {
    private Long routeId;
    private String name;
    private String inputUri;
    private String outputUri;
    private Map<String, Object> config;
    private boolean active;
    
    // Input endpoint
    private String inputEndpointType;
    private Map<String, Object> inputConfig;
    
    // Output endpoint
    private String outputEndpointType;
    private Map<String, Object> outputConfig;
    
    // Processing components
    private java.util.List<ComponentDefinition> components;
}
