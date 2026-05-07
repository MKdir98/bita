package ir.bita.esm.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.service.command.CreateServiceCollectionCommand;
import ir.bita.esm.service.dto.ServiceCollectionResponse;
import ir.bita.esm.service.dto.ServiceResponse;
import ir.bita.esm.service.entity.ServiceCollection;
import ir.bita.esm.service.handler.CreateServiceCollectionHandler;
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
 * REST controller for Service Collection management.
 */
@RestController
@RequestMapping("/api/v1/service-collections")
@RequiredArgsConstructor
@Tag(name = "Service Collections", description = "Service collection management endpoints")
public class ServiceCollectionController {

    private final CreateServiceCollectionHandler createHandler;
    private final ServiceQueryService queryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Create collection", description = "Create a new service collection")
    public ResponseEntity<ServiceCollectionResponse> createCollection(
            @Valid @RequestBody CreateServiceCollectionCommand command
    ) {
        ServiceCollection collection = createHandler.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getCollection(collection.getId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "List collections", description = "List all service collections")
    public ResponseEntity<Page<ServiceCollectionResponse>> listCollections(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ServiceCollectionResponse> collections;
        if (search != null && !search.isEmpty()) {
            collections = queryService.searchCollections(search, pageable);
        } else {
            collections = queryService.listCollections(pageable);
        }
        return ResponseEntity.ok(collections);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get collection", description = "Get service collection by ID")
    public ResponseEntity<ServiceCollectionResponse> getCollection(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getCollection(id));
    }

    @GetMapping("/{id}/services")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get services in collection", description = "Get all services in a collection")
    public ResponseEntity<List<ServiceResponse>> getServicesInCollection(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getServicesInCollection(id));
    }
}
