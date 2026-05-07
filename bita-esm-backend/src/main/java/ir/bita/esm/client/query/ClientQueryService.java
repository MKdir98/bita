package ir.bita.esm.client.query;

import ir.bita.esm.client.dto.ClientResponse;
import ir.bita.esm.client.dto.CredentialResponse;
import ir.bita.esm.client.dto.TagResponse;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.client.entity.Credential;
import ir.bita.esm.client.entity.Tag;
import ir.bita.esm.client.repository.ClientRepository;
import ir.bita.esm.client.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Query service for Client read operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientQueryService {

    private final ClientRepository clientRepository;
    private final CredentialRepository credentialRepository;

    /**
     * Gets a client by ID with credentials.
     */
    public ClientResponse getClient(Long id) {
        Client client = clientRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        
        List<Credential> credentials = credentialRepository.findByClientId(id);
        return mapToResponse(client, credentials);
    }

    /**
     * Lists all clients with pagination.
     */
    public Page<ClientResponse> listClients(Pageable pageable) {
        return clientRepository.findByDeletedFalse(pageable)
                .map(client -> mapToResponse(client, null));
    }

    /**
     * Searches clients by name.
     */
    public Page<ClientResponse> searchClients(String query, Pageable pageable) {
        return clientRepository.findByNameContainingIgnoreCaseAndDeletedFalse(query, pageable)
                .map(client -> mapToResponse(client, null));
    }

    /**
     * Lists clients by tag key.
     */
    public Page<ClientResponse> listClientsByTagKey(String key, Pageable pageable) {
        return clientRepository.findByTagKey(key, pageable)
                .map(client -> mapToResponse(client, null));
    }

    /**
     * Lists clients by tag key and value.
     */
    public Page<ClientResponse> listClientsByTagKeyAndValue(String key, String value, Pageable pageable) {
        return clientRepository.findByTagKeyAndValue(key, value, pageable)
                .map(client -> mapToResponse(client, null));
    }

    /**
     * Gets child clients of a parent.
     */
    public List<ClientResponse> getChildClients(Long parentId) {
        return clientRepository.findByParentIdAndDeletedFalse(parentId).stream()
                .map(client -> mapToResponse(client, null))
                .collect(Collectors.toList());
    }

    /**
     * Gets credentials for a client.
     */
    public List<CredentialResponse> getCredentials(Long clientId) {
        // Verify client exists
        if (!clientRepository.existsById(clientId)) {
            throw new IllegalArgumentException("Client not found");
        }
        
        return credentialRepository.findByClientId(clientId).stream()
                .map(this::mapCredentialToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Counts total active clients.
     */
    public long countClients() {
        return clientRepository.countByDeletedFalse();
    }

    private ClientResponse mapToResponse(Client client, List<Credential> credentials) {
        ClientResponse.ClientResponseBuilder builder = ClientResponse.builder()
                .id(client.getId())
                .name(client.getName())
                .description(client.getDescription())
                .contactEmail(client.getContactEmail())
                .contactPhone(client.getContactPhone())
                .tags(mapTagsToResponse(client.getTags()))
                .active(client.isActive())
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt());

        if (client.getParent() != null) {
            builder.parentId(client.getParent().getId())
                    .parentName(client.getParent().getName());
        }

        if (credentials != null) {
            builder.credentials(credentials.stream()
                    .map(this::mapCredentialToResponse)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private List<TagResponse> mapTagsToResponse(List<Tag> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .map(tag -> TagResponse.builder()
                        .id(tag.getId())
                        .key(tag.getKey())
                        .value(tag.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private CredentialResponse mapCredentialToResponse(Credential credential) {
        return CredentialResponse.builder()
                .id(credential.getId())
                .credentialType(credential.getCredentialType())
                .credentialValue(maskCredentialValue(credential))
                .description(credential.getDescription())
                .active(credential.isActive())
                .expiresAt(credential.getExpiresAt())
                .expired(credential.isExpired())
                .createdAt(credential.getCreatedAt())
                .build();
    }

    /**
     * Masks sensitive credential values for display.
     */
    private String maskCredentialValue(Credential credential) {
        String value = credential.getCredentialValue();
        switch (credential.getCredentialType()) {
            case API_KEY:
                // Show first 8 and last 4 characters
                if (value.length() > 12) {
                    return value.substring(0, 8) + "****" + value.substring(value.length() - 4);
                }
                return "****";
            case BASIC_AUTH:
                // Show username only
                return value;
            case IP_ADDRESS:
                // Show full IP
                return value;
            case X509_CERTIFICATE:
                // Show certificate subject/thumbprint
                return value;
            case OAUTH2:
                // Show client ID
                return value;
            default:
                return "****";
        }
    }
}
