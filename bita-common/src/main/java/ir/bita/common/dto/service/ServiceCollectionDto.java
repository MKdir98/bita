package ir.bita.common.dto.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing a service collection.
 * A collection groups related services under a common base path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCollectionDto {

    private Long id;
    
    private String name;
    
    /**
     * Base path for all services in this collection.
     * Example: "/esb/payment"
     */
    private String basePath;
    
    private String description;
    
    private List<ServiceDto> services;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
