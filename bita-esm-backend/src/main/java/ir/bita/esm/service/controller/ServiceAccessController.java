package ir.bita.esm.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.service.command.GrantAccessCommand;
import ir.bita.esm.service.command.RevokeAccessCommand;
import ir.bita.esm.service.dto.ServiceAccessResponse;
import ir.bita.esm.service.entity.ServiceAccess;
import ir.bita.esm.service.handler.GrantAccessHandler;
import ir.bita.esm.service.handler.RevokeAccessHandler;
import ir.bita.esm.service.query.ServiceQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Service Access management.
 */
@RestController
@RequestMapping("/api/v1/access")
@RequiredArgsConstructor
@Tag(name = "Service Access", description = "Service access management endpoints")
public class ServiceAccessController {

    private final GrantAccessHandler grantAccessHandler;
    private final RevokeAccessHandler revokeAccessHandler;
    private final ServiceQueryService queryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCESS_MANAGER')")
    @Operation(summary = "Grant access", description = "Grant client access to a service")
    public ResponseEntity<ServiceAccessResponse> grantAccess(@Valid @RequestBody GrantAccessCommand command) {
        ServiceAccess access = grantAccessHandler.handle(command);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ServiceAccessResponse.builder()
                        .id(access.getId())
                        .clientId(access.getClient().getId())
                        .clientName(access.getClient().getName())
                        .serviceId(access.getService().getId())
                        .serviceName(access.getService().getName())
                        .serviceVersion(access.getService().getServiceVersion())
                        .status(access.getStatus())
                        .customRateLimit(access.getCustomRateLimit())
                        .validFrom(access.getValidFrom())
                        .validUntil(access.getValidUntil())
                        .grantReason(access.getGrantReason())
                        .currentlyValid(access.isCurrentlyValid())
                        .createdAt(access.getCreatedAt())
                        .build()
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCESS_MANAGER', 'VIEWER')")
    @Operation(summary = "List access", description = "List all access rules")
    public ResponseEntity<Page<ServiceAccessResponse>> listAccess(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(queryService.listAccess(pageable));
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCESS_MANAGER', 'CLIENT_MANAGER')")
    @Operation(summary = "Get client access", description = "Get all services a client has access to")
    public ResponseEntity<List<ServiceAccessResponse>> getClientAccess(@PathVariable Long clientId) {
        return ResponseEntity.ok(queryService.getAccessByClient(clientId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCESS_MANAGER')")
    @Operation(summary = "Revoke access", description = "Revoke client access to a service")
    public ResponseEntity<Void> revokeAccess(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        revokeAccessHandler.handle(RevokeAccessCommand.builder()
                .accessId(id)
                .reason(reason)
                .build());
        return ResponseEntity.noContent().build();
    }
}
