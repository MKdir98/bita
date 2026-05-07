package ir.bita.esm.audit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.audit.dto.AuditHistoryResponse;
import ir.bita.esm.audit.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit trail queries")
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping("/{entityType}/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'VIEWER')")
    @Operation(summary = "Get entity revision history")
    public ResponseEntity<AuditHistoryResponse> getEntityHistory(
            @PathVariable String entityType,
            @PathVariable Long id) {
        return ResponseEntity.ok(auditQueryService.getEntityHistory(entityType, id));
    }

    @GetMapping("/{entityType}/{id}/revisions/{revisionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VIEWER')")
    @Operation(summary = "Get entity state at specific revision")
    public ResponseEntity<Map<String, Object>> getEntityAtRevision(
            @PathVariable String entityType,
            @PathVariable Long id,
            @PathVariable Long revisionId) {
        return ResponseEntity.ok(auditQueryService.getEntityAtRevision(entityType, id, revisionId));
    }

    @GetMapping("/revisions/{revisionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all changes in a revision")
    public ResponseEntity<List<Map<String, Object>>> getRevisionChanges(
            @PathVariable Long revisionId) {
        return ResponseEntity.ok(auditQueryService.getRevisionChanges(revisionId));
    }
}
