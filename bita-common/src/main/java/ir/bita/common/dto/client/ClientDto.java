package ir.bita.common.dto.client;

import ir.bita.common.dto.credential.CredentialDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing a client (organization) in the system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDto {

    private Long id;
    
    private String name;
    
    private String description;
    
    private String contactEmail;
    
    private String contactPhone;
    
    private List<TagDto> tags;
    
    private List<CredentialDto> credentials;
    
    private boolean active;
    
    private boolean deleted;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private String createdBy;
}
