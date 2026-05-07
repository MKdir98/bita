package ir.bita.esm.route.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.common.domain.FileType;
import ir.bita.esm.route.command.*;
import ir.bita.esm.route.dto.RouteDefinition;
import ir.bita.esm.route.dto.RouteResponse;
import ir.bita.esm.route.entity.Route;
import ir.bita.esm.route.entity.RouteFile;
import ir.bita.esm.route.handler.*;
import ir.bita.esm.route.query.RouteQueryService;
import ir.bita.esm.route.service.RouteBuilderService;
import ir.bita.esm.route.service.RouteFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Routes", description = "Route management")
public class RouteController {

    private final CreateRouteHandler createHandler;
    private final UpdateRouteHandler updateHandler;
    private final DeleteRouteHandler deleteHandler;
    private final RouteQueryService queryService;
    private final RouteBuilderService builderService;
    private final RouteFileService fileService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Create route")
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody CreateRouteCommand cmd) {
        Route route = createHandler.handle(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getRoute(route.getId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "List routes")
    public ResponseEntity<Page<RouteResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(queryService.listRoutes(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get route by ID")
    public ResponseEntity<RouteResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getRoute(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Update route")
    public ResponseEntity<RouteResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateRouteCommand cmd) {
        cmd.setRouteId(id);
        Route route = updateHandler.handle(cmd);
        return ResponseEntity.ok(queryService.getRoute(route.getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Delete route")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteHandler.handle(DeleteRouteCommand.builder().routeId(id).build());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/definition")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get route definition for ESB")
    public ResponseEntity<RouteDefinition> getDefinition(@PathVariable Long id) {
        return ResponseEntity.ok(builderService.buildRoute(id));
    }

    @GetMapping("/{id}/definition/yaml")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Get route definition as YAML")
    public ResponseEntity<String> getDefinitionYaml(@PathVariable Long id) {
        RouteDefinition definition = builderService.buildRoute(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/yaml"))
                .body(builderService.toYaml(definition));
    }

    // File endpoints

    @PostMapping("/{id}/files")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Upload file to route")
    public ResponseEntity<RouteResponse.FileResponse> uploadFile(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") FileType fileType) {
        RouteFile uploaded = fileService.uploadFile(id, file, fileType);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                RouteResponse.FileResponse.builder()
                        .id(uploaded.getId())
                        .filename(uploaded.getFilename())
                        .fileType(uploaded.getFileType())
                        .fileSize(uploaded.getFileSize())
                        .mimeType(uploaded.getMimeType())
                        .createdAt(uploaded.getCreatedAt())
                        .build());
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "List files for route")
    public ResponseEntity<List<RouteResponse.FileResponse>> listFiles(@PathVariable Long id) {
        return ResponseEntity.ok(fileService.getFilesForRoute(id).stream()
                .map(f -> RouteResponse.FileResponse.builder()
                        .id(f.getId()).filename(f.getFilename()).fileType(f.getFileType())
                        .fileSize(f.getFileSize()).mimeType(f.getMimeType()).createdAt(f.getCreatedAt())
                        .build())
                .toList());
    }

    @GetMapping("/{routeId}/files/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER', 'VIEWER')")
    @Operation(summary = "Download file")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long routeId, @PathVariable Long fileId) {
        RouteFile file = fileService.getFile(fileId);
        Resource resource = fileService.downloadFile(fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(file.getMimeType() != null ? file.getMimeType() : "application/octet-stream"))
                .body(resource);
    }

    @DeleteMapping("/{routeId}/files/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_MANAGER')")
    @Operation(summary = "Delete file from route")
    public ResponseEntity<Void> deleteFile(@PathVariable Long routeId, @PathVariable Long fileId) {
        fileService.deleteFile(routeId, fileId);
        return ResponseEntity.noContent().build();
    }
}
