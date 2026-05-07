package ir.bita.esm.route.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.route.command.CreateEndpointTemplateCommand;
import ir.bita.esm.route.dto.EndpointTemplateResponse;
import ir.bita.esm.route.entity.EndpointTemplate;
import ir.bita.esm.route.handler.CreateEndpointTemplateHandler;
import ir.bita.esm.route.query.RouteQueryService;
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

@RestController
@RequestMapping("/api/v1/endpoint-templates")
@RequiredArgsConstructor
@Tag(name = "Endpoint Templates", description = "Endpoint template management")
public class EndpointTemplateController {

    private final CreateEndpointTemplateHandler createHandler;
    private final RouteQueryService queryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Create endpoint template")
    public ResponseEntity<EndpointTemplateResponse> create(@Valid @RequestBody CreateEndpointTemplateCommand cmd) {
        EndpointTemplate template = createHandler.handle(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getEndpointTemplate(template.getId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "List endpoint templates")
    public ResponseEntity<Page<EndpointTemplateResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(queryService.listEndpointTemplates(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get endpoint template by ID")
    public ResponseEntity<EndpointTemplateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getEndpointTemplate(id));
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "List endpoint templates by category")
    public ResponseEntity<List<EndpointTemplateResponse>> listByCategory(@PathVariable String category) {
        return ResponseEntity.ok(queryService.listEndpointTemplatesByCategory(category));
    }
}
