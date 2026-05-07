package ir.bita.esm.client.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.client.command.*;
import ir.bita.esm.client.dto.ClientResponse;
import ir.bita.esm.client.dto.CredentialResponse;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.client.entity.Credential;
import ir.bita.esm.client.handler.*;
import ir.bita.esm.client.query.ClientQueryService;
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
 * REST controller for Client management.
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Client management endpoints")
public class ClientController {

    private final CreateClientHandler createClientHandler;
    private final UpdateClientHandler updateClientHandler;
    private final DeleteClientHandler deleteClientHandler;
    private final AddCredentialHandler addCredentialHandler;
    private final RemoveCredentialHandler removeCredentialHandler;
    private final ClientQueryService queryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_MANAGER')")
    @Operation(summary = "Create client", description = "Create a new client")
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody CreateClientCommand command) {
        Client client = createClientHandler.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.getClient(client.getId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_MANAGER', 'VIEWER')")
    @Operation(summary = "List clients", description = "List all clients with pagination")
    public ResponseEntity<Page<ClientResponse>> listClients(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ClientResponse> clients;
        if (search != null && !search.isEmpty()) {
            clients = queryService.searchClients(search, pageable);
        } else {
            clients = queryService.listClients(pageable);
        }
        return ResponseEntity.ok(clients);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_MANAGER', 'VIEWER')")
    @Operation(summary = "Get client", description = "Get client by ID with credentials")
    public ResponseEntity<ClientResponse> getClient(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getClient(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_MANAGER')")
    @Operation(summary = "Update client", description = "Update an existing client")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClientCommand command
    ) {
        command.setClientId(id);
        Client client = updateClientHandler.handle(command);
        return ResponseEntity.ok(queryService.getClient(client.getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_MANAGER')")
    @Operation(summary = "Delete client", description = "Soft delete a client")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        deleteClientHandler.handle(DeleteClientCommand.builder().clientId(id).build());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/children")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_MANAGER', 'VIEWER')")
    @Operation(summary = "Get child clients", description = "Get child clients of a parent client")
    public ResponseEntity<List<ClientResponse>> getChildClients(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getChildClients(id));
    }

    // Credential endpoints

    @GetMapping("/{id}/credentials")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_MANAGER')")
    @Operation(summary = "List credentials", description = "List all credentials for a client")
    public ResponseEntity<List<CredentialResponse>> listCredentials(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getCredentials(id));
    }

    @PostMapping("/{id}/credentials")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_MANAGER')")
    @Operation(summary = "Add credential", description = "Add a credential to a client")
    public ResponseEntity<CredentialResponse> addCredential(
            @PathVariable Long id,
            @Valid @RequestBody AddCredentialCommand command
    ) {
        command.setClientId(id);
        Credential credential = addCredentialHandler.handle(command);
        
        // Return the credential response
        return ResponseEntity.status(HttpStatus.CREATED).body(
                CredentialResponse.builder()
                        .id(credential.getId())
                        .credentialType(credential.getCredentialType())
                        .credentialValue(credential.getCredentialValue())
                        .description(credential.getDescription())
                        .active(credential.isActive())
                        .expiresAt(credential.getExpiresAt())
                        .expired(credential.isExpired())
                        .createdAt(credential.getCreatedAt())
                        .build()
        );
    }

    @DeleteMapping("/{clientId}/credentials/{credentialId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_MANAGER')")
    @Operation(summary = "Remove credential", description = "Remove a credential from a client")
    public ResponseEntity<Void> removeCredential(
            @PathVariable Long clientId,
            @PathVariable Long credentialId
    ) {
        removeCredentialHandler.handle(RemoveCredentialCommand.builder()
                .clientId(clientId)
                .credentialId(credentialId)
                .build());
        return ResponseEntity.noContent().build();
    }
}
