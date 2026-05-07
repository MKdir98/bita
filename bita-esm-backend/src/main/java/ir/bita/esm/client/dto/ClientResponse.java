package ir.bita.esm.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for Client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientResponse {

    private Long id;
    private String name;
    private String description;
    private String contactEmail;
    private String contactPhone;
    private List<TagResponse> tags;
    private boolean active;
    private Long parentId;
    private String parentName;
    private List<CredentialResponse> credentials;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
