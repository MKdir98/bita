package ir.bita.esm.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.common.domain.ServicePhase;
import ir.bita.esm.service.command.ChangeServicePhaseCommand;
import ir.bita.esm.service.command.CreateServiceCommand;
import ir.bita.esm.service.command.UpdateServiceScalingCommand;
import ir.bita.esm.service.dto.ServiceAccessResponse;
import ir.bita.esm.service.dto.ServiceResponse;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.handler.ChangeServicePhaseHandler;
import ir.bita.esm.service.handler.CreateServiceHandler;
import ir.bita.esm.service.handler.UpdateServiceScalingHandler;
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
 * REST controller for Service management.
 */
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
@Tag(name = "Services", description = "Service management endpoints")
public class ServiceController {

    private final CreateServiceHandler createHandler;
    private final ChangeServicePhaseHandler changePhaseHandler;
    private final UpdateServiceScalingHandler updateScalingHandler;
    private final ServiceQueryService queryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Create service", description = "Create a new service")
    public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody CreateServiceCommand command) {
        ServiceEntity service = createHandler.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getService(service.getId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "List services", description = "List all services with optional phase filter")
    public ResponseEntity<Page<ServiceResponse>> listServices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ServicePhase phase,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ServiceResponse> services;
        if (search != null && !search.isEmpty()) {
            services = queryService.searchServices(search, pageable);
        } else if (phase != null) {
            services = queryService.listServicesByPhase(phase, pageable);
        } else {
            services = queryService.listServices(pageable);
        }
        return ResponseEntity.ok(services);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get service", description = "Get service by ID")
    public ResponseEntity<ServiceResponse> getService(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getService(id));
    }

    @PostMapping("/{id}/phase")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Change phase", description = "Change service phase (triggers K8s deployment/undeployment)")
    public ResponseEntity<ServiceResponse> changePhase(
            @PathVariable Long id,
            @Valid @RequestBody ChangeServicePhaseCommand command
    ) {
        command.setServiceId(id);
        ServiceEntity service = changePhaseHandler.handle(command);
        return ResponseEntity.ok(queryService.getService(service.getId()));
    }

    @PutMapping("/{id}/scaling")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Update scaling", description = "Update service scaling configuration")
    public ResponseEntity<ServiceResponse> updateScaling(
            @PathVariable Long id,
            @Valid @RequestBody UpdateServiceScalingCommand command
    ) {
        command.setServiceId(id);
        ServiceEntity service = updateScalingHandler.handle(command);
        return ResponseEntity.ok(queryService.getService(service.getId()));
    }

    @GetMapping("/{id}/access")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'ACCESS_MANAGER')")
    @Operation(summary = "Get service access", description = "Get all clients with access to this service")
    public ResponseEntity<List<ServiceAccessResponse>> getServiceAccess(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getAccessByService(id));
    }
}
