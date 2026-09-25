package ir.bita.esm.route.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.route.dto.GroovyTemplateRequest;
import ir.bita.esm.route.dto.GroovyTemplateResponse;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.repository.GroovyTemplateRepository;
import ir.bita.esm.route.service.ScriptAssemblyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/groovy-templates")
@RequiredArgsConstructor
@Tag(name = "Groovy Templates", description = "Groovy service script template management")
public class GroovyTemplateController {

    private final GroovyTemplateRepository repository;
    private final ScriptAssemblyService assemblyService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Create groovy template")
    @Transactional
    public ResponseEntity<GroovyTemplateResponse> create(@Valid @RequestBody GroovyTemplateRequest req) {
        List<GroovyTemplate.VariableMetadata> vars = req.getVariables() == null || req.getVariables().isEmpty()
                ? assemblyService.scanVariables(req.getScriptText())
                : mapRequestVars(req);

        GroovyTemplate template = GroovyTemplate.builder()
                .name(req.getName())
                .description(req.getDescription())
                .scriptText(req.getScriptText())
                .variables(vars)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(repository.save(template)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "List groovy templates")
    public ResponseEntity<Page<GroovyTemplateResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(repository.findByDeletedFalse(pageable).map(this::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get groovy template by ID")
    public ResponseEntity<GroovyTemplateResponse> get(@PathVariable Long id) {
        return repository.findByIdAndDeletedFalse(id)
                .map(t -> ResponseEntity.ok(toResponse(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Update groovy template")
    @Transactional
    public ResponseEntity<GroovyTemplateResponse> update(@PathVariable Long id,
                                                          @Valid @RequestBody GroovyTemplateRequest req) {
        return repository.findByIdAndDeletedFalse(id)
                .map(t -> {
                    t.setName(req.getName());
                    t.setDescription(req.getDescription());
                    t.setScriptText(req.getScriptText());
                    t.setVariables(req.getVariables() == null || req.getVariables().isEmpty()
                            ? assemblyService.scanVariables(req.getScriptText())
                            : mapRequestVars(req));
                    return ResponseEntity.ok(toResponse(repository.save(t)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Delete groovy template (soft delete)")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return repository.findByIdAndDeletedFalse(id)
                .map(t -> {
                    t.softDelete();
                    repository.save(t);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().<Void>build());
    }

    private List<GroovyTemplate.VariableMetadata> mapRequestVars(GroovyTemplateRequest req) {
        if (req.getVariables() == null) return new ArrayList<>();
        return req.getVariables().stream().map(v -> {
            GroovyTemplate.VariableMetadata m = new GroovyTemplate.VariableMetadata();
            m.setName(v.getName());
            m.setLabel(v.getLabel());
            m.setDescription(v.getDescription());
            m.setType(v.getType());
            m.setRequired(v.isRequired());
            m.setDefaultValue(v.getDefaultValue());
            m.setPath(v.getPath());
            return m;
        }).collect(Collectors.toList());
    }

    GroovyTemplateResponse toResponse(GroovyTemplate t) {
        return GroovyTemplateResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .description(t.getDescription())
                .scriptText(t.getScriptText())
                .variables(t.getVariables() == null ? List.of() :
                        t.getVariables().stream().map(v -> GroovyTemplateResponse.VariableMeta.builder()
                                .name(v.getName()).label(v.getLabel()).description(v.getDescription())
                                .type(v.getType()).required(v.isRequired())
                                .defaultValue(v.getDefaultValue()).path(v.getPath())
                                .build()).collect(Collectors.toList()))
                .active(t.isActive())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
