package ir.bita.common.dto.route;

import ir.bita.common.domain.ComponentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO representing a component (processor or bean) in a route.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentDto {

    private Long id;
    
    private Long templateId;
    
    private String templateName;
    
    private String name;
    
    private String description;
    
    private ComponentType type;
    
    /**
     * Order of this component in the route processing chain.
     */
    private Integer orderIndex;
    
    /**
     * Fully qualified class name of the processor or bean.
     */
    private String className;
    
    /**
     * Configuration values for this component.
     */
    private Map<String, Object> config;
}
