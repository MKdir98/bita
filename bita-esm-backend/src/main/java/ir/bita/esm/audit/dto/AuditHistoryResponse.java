package ir.bita.esm.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditHistoryResponse {
    private String entityType;
    private Long entityId;
    private int totalRevisions;
    private List<RevisionEntry> revisions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevisionEntry {
        private Long revisionId;
        private String revisionDate;
        private String revisionType;
        private Long userId;
        private String username;
        private Map<String, Object> entityData;
    }
}
