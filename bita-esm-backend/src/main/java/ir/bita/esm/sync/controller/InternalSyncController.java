package ir.bita.esm.sync.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.sync.dto.*;
import ir.bita.esm.sync.service.SyncDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal API controller for ESB core synchronization.
 * These endpoints are secured via API key, not JWT.
 */
@RestController
@RequestMapping("/internal/v1/sync")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal Sync API", description = "Internal endpoints for ESB core synchronization")
public class InternalSyncController {

    private final SyncDataService syncDataService;

    @GetMapping("/clients")
    @Operation(summary = "Get all clients with credentials", description = "For ESB core client authentication")
    public ResponseEntity<List<ClientSyncDto>> getClients() {
        log.debug("Internal sync request: clients");
        return ResponseEntity.ok(syncDataService.getAllClientsWithCredentials());
    }

    @GetMapping("/services")
    @Operation(summary = "Get all services with routes", description = "For ESB core route configuration")
    public ResponseEntity<List<ServiceSyncDto>> getServices() {
        log.debug("Internal sync request: services");
        return ResponseEntity.ok(syncDataService.getAllServicesWithRoutes());
    }

    @GetMapping("/routes")
    @Operation(summary = "Get all routes with files", description = "For ESB core route definitions")
    public ResponseEntity<List<RouteSyncDto>> getRoutes() {
        log.debug("Internal sync request: routes");
        return ResponseEntity.ok(syncDataService.getAllRoutes());
    }

    @GetMapping("/access")
    @Operation(summary = "Get all access rules", description = "For ESB core access control")
    public ResponseEntity<List<AccessSyncDto>> getAccessRules() {
        log.debug("Internal sync request: access");
        return ResponseEntity.ok(syncDataService.getAllAccessRules());
    }

    @GetMapping("/full")
    @Operation(summary = "Get full sync data", description = "Complete data for ESB core initialization")
    public ResponseEntity<FullSyncDataDto> getFullSyncData() {
        log.info("Internal sync request: full sync");
        return ResponseEntity.ok(syncDataService.getFullSyncData());
    }
}
