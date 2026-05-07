package ir.bita.common.dto.route;

import ir.bita.common.domain.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing a file attached to a route.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteFileDto {

    private Long id;
    
    private Long routeId;
    
    private String filename;
    
    private FileType fileType;
    
    /**
     * Size of the file in bytes.
     */
    private Long fileSize;
    
    /**
     * MIME type of the file.
     */
    private String mimeType;
    
    /**
     * Storage path or URL for the file.
     */
    private String storagePath;
    
    /**
     * SHA-256 hash of the file content.
     */
    private String contentHash;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
