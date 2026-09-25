package ir.bita.esm.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.route.dto.ServiceGroovyConfigRequest;
import ir.bita.esm.route.dto.ServiceGroovyConfigResponse;
import ir.bita.esm.route.entity.ServiceGroovyConfig;
import ir.bita.esm.route.repository.GroovyTemplateRepository;
import ir.bita.esm.route.repository.ServiceGroovyConfigRepository;
import ir.bita.esm.route.service.ScriptAssemblyService;
import ir.bita.esm.service.repository.ServiceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/services/{serviceId}/groovy-config")
@RequiredArgsConstructor
@Tag(name = "Service Groovy Config", description = "Assign and configure Groovy templates for services")
public class ServiceGroovyConfigController {

    private final ServiceGroovyConfigRepository configRepository;
    private final GroovyTemplateRepository templateRepository;
    private final ServiceRepository serviceRepository;
    private final ScriptAssemblyService assemblyService;
    private final ir.bita.esm.route.service.ServiceConfigVersionService versionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get Groovy config for service")
    public ResponseEntity<ServiceGroovyConfigResponse> get(@PathVariable Long serviceId) {
        return configRepository.findByServiceId(serviceId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Assign Groovy template to service and set variable values")
    @Transactional
    public ResponseEntity<ServiceGroovyConfigResponse> create(@PathVariable Long serviceId,
                                                               @Valid @RequestBody ServiceGroovyConfigRequest req) {
        var service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));
        var template = templateRepository.findByIdAndDeletedFalse(req.getGroovyTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("GroovyTemplate not found: " + req.getGroovyTemplateId()));

        if (configRepository.existsByServiceId(serviceId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        requireValidVariables(template, req.getVariableValues());
        versionService.recordAndActivate(service, template, req.getVariableValues(),
                assemblyService.assemble(template), "created via API");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(configRepository.findByServiceId(serviceId).orElseThrow()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Update Groovy template assignment or variable values")
    @Transactional
    public ResponseEntity<ServiceGroovyConfigResponse> update(@PathVariable Long serviceId,
                                                               @Valid @RequestBody ServiceGroovyConfigRequest req) {
        return configRepository.findByServiceId(serviceId)
                .map(config -> {
                    var template = templateRepository.findByIdAndDeletedFalse(req.getGroovyTemplateId())
                            .orElseThrow(() -> new IllegalArgumentException("GroovyTemplate not found: " + req.getGroovyTemplateId()));
                    requireValidVariables(template, req.getVariableValues());
                    versionService.recordAndActivate(config.getService(), template, req.getVariableValues(),
                            assemblyService.assemble(template), "updated via API");
                    return ResponseEntity.ok(toResponse(configRepository.findByServiceId(serviceId).orElseThrow()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static void requireValidVariables(ir.bita.esm.route.entity.GroovyTemplate template,
                                              java.util.Map<String, Object> values) {
        var problems = ir.bita.esm.llm.tool.ComponentInstanceTool.validateVariables(
                template, values == null ? java.util.Map.of() : values);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }

    private ServiceGroovyConfigResponse toResponse(ServiceGroovyConfig c) {
        return ServiceGroovyConfigResponse.builder()
                .id(c.getId())
                .serviceId(c.getService() != null ? c.getService().getId() : null)
                .groovyTemplateId(c.getGroovyTemplate() != null ? c.getGroovyTemplate().getId() : null)
                .groovyTemplateName(c.getGroovyTemplate() != null ? c.getGroovyTemplate().getName() : null)
                .variableValues(c.getVariableValues())
                .assembledScript(c.getAssembledScript())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
