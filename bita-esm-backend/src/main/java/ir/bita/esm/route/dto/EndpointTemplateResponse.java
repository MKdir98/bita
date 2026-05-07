package ir.bita.esm.route.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointTemplateResponse {
    private Long id;
    private String name;
    private String description;
    private String camelYaml;
    private List<Long> attachmentIds;
    private Map<String, Object> configSchema;
    private Integer defaultRateLimit;
    private String category;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
