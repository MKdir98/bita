package ir.bita.esm.route.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.route.command.CreateRouteTemplateCommand;
import ir.bita.esm.route.dto.RouteTemplateResponse;
import ir.bita.esm.route.entity.RouteTemplate;
import ir.bita.esm.route.handler.CreateRouteTemplateHandler;
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

@RestController
@RequestMapping("/api/v1/route-templates")
@RequiredArgsConstructor
@Tag(name = "Route Templates", description = "Route template management")
public class RouteTemplateController {

    private final CreateRouteTemplateHandler createHandler;
    private final RouteQueryService queryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Create route template")
    public ResponseEntity<RouteTemplateResponse> create(@Valid @RequestBody CreateRouteTemplateCommand cmd) {
        RouteTemplate template = createHandler.handle(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getRouteTemplate(template.getId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "List route templates")
    public ResponseEntity<Page<RouteTemplateResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(queryService.listRouteTemplates(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get route template by ID")
    public ResponseEntity<RouteTemplateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getRouteTemplate(id));
    }
}
