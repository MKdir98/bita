package ir.bita.esm.route.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.route.command.CreateComponentTemplateCommand;
import ir.bita.esm.route.dto.ComponentTemplateResponse;
import ir.bita.esm.route.entity.ComponentTemplate;
import ir.bita.esm.route.handler.CreateComponentTemplateHandler;
import ir.bita.esm.route.query.RouteQueryService;
import ir.bita.esm.route.repository.ComponentTemplateRepository;
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
@RequestMapping("/api/v1/component-templates")
@RequiredArgsConstructor
@Tag(name = "Component Templates", description = "Component template management")
public class ComponentTemplateController {

    private final CreateComponentTemplateHandler createHandler;
    private final RouteQueryService queryService;
    private final ComponentTemplateRepository componentTemplateRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Create component template")
    public ResponseEntity<ComponentTemplateResponse> create(@Valid @RequestBody CreateComponentTemplateCommand cmd) {
        ComponentTemplate template = createHandler.handle(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getComponentTemplate(template.getId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "List component templates")
    public ResponseEntity<Page<ComponentTemplateResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(queryService.listComponentTemplates(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get component template by ID")
    public ResponseEntity<ComponentTemplateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getComponentTemplate(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Update component template")
    public ResponseEntity<ComponentTemplateResponse> update(@PathVariable Long id,
                                                            @Valid @RequestBody CreateComponentTemplateCommand cmd) {
        ComponentTemplate template = componentTemplateRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Component template not found"));
        template.setName(cmd.getName());
        template.setDescription(cmd.getDescription());
        template.setComponentType(cmd.getComponentType());
        template.setClassName(cmd.getClassName());
        template.setConfigSchema(cmd.getConfigSchema());
        template.setCategory(cmd.getCategory());
        template.setGroovyCode(cmd.getGroovyCode());
        componentTemplateRepository.save(template);
        return ResponseEntity.ok(queryService.getComponentTemplate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Delete component template")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ComponentTemplate template = componentTemplateRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Component template not found"));
        template.softDelete();
        componentTemplateRepository.save(template);
        return ResponseEntity.noContent().build();
    }
}
