package ir.bita.esb.route;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Component definition within a route.
 */
@Data
@Builder
public class ComponentDefinition {
    private Long componentId;
    private String name;
    private String type;
    private int order;
    private Map<String, Object> config;
}
