package ir.bita.esm.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for ServiceCollection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCollectionResponse {

    private Long id;
    private String name;
    private String basePath;
    private String description;
    private boolean active;
    private int serviceCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
