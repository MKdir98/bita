package ir.bita.esm.sync.service;

import ir.bita.common.domain.ServicePhase;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.client.entity.Credential;
import ir.bita.esm.client.repository.ClientRepository;
import ir.bita.esm.client.repository.CredentialRepository;
import ir.bita.esm.route.entity.*;
import ir.bita.esm.route.repository.ComponentRepository;
import ir.bita.esm.route.repository.RouteFileRepository;
import ir.bita.esm.route.repository.RouteRepository;
import ir.bita.esm.service.entity.ServiceAccess;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.repository.ServiceAccessRepository;
import ir.bita.esm.service.repository.ServiceRepository;
import ir.bita.esm.sync.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for aggregating sync data for ESB core.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SyncDataService {

    private final ClientRepository clientRepository;
    private final CredentialRepository credentialRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceAccessRepository accessRepository;
    private final RouteRepository routeRepository;
    private final ComponentRepository componentRepository;
    private final RouteFileRepository fileRepository;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    /**
     * Gets full sync data for ESB core.
     */
    public FullSyncDataDto getFullSyncData() {
        log.info("Generating full sync data");

        List<ClientSyncDto> clients = getAllClientsWithCredentials();
        List<ServiceSyncDto> services = getAllServicesWithRoutes();
        List<AccessSyncDto> accessRules = getAllAccessRules();

        FullSyncDataDto.Statistics stats = FullSyncDataDto.Statistics.builder()
                .totalClients(clientRepository.countByDeletedFalse())
                .totalServices(serviceRepository.count())
                .activeServices(serviceRepository.countByPhaseAndDeletedFalse(ServicePhase.ACTIVE))
                .totalRoutes(routeRepository.countByDeletedFalse())
                .totalAccessRules(accessRepository.count())
                .build();

        return FullSyncDataDto.builder()
                .syncTimestamp(LocalDateTime.now())
                .version(appVersion)
                .clients(clients)
                .services(services)
                .accessRules(accessRules)
                .statistics(stats)
                .build();
    }

    /**
     * Gets all clients with their credentials.
     */
    public List<ClientSyncDto> getAllClientsWithCredentials() {
        return clientRepository.findByDeletedFalse(Pageable.unpaged()).stream()
                .map(this::mapClientToSync)
                .collect(Collectors.toList());
    }

    /**
     * Gets all services with their routes.
     */
    public List<ServiceSyncDto> getAllServicesWithRoutes() {
        return serviceRepository.findByDeletedFalse(Pageable.unpaged()).stream()
                .map(this::mapServiceToSync)
                .collect(Collectors.toList());
    }

    /**
     * Gets all routes with their files.
     */
    public List<RouteSyncDto> getAllRoutes() {
        return routeRepository.findByDeletedFalse(Pageable.unpaged()).stream()
                .map(this::mapRouteToSync)
                .collect(Collectors.toList());
    }

    /**
     * Gets all access rules.
     */
    public List<AccessSyncDto> getAllAccessRules() {
        return accessRepository.findAll(Pageable.unpaged()).stream()
                .map(this::mapAccessToSync)
                .collect(Collectors.toList());
    }

    // Mappers

    private ClientSyncDto mapClientToSync(Client client) {
        List<Credential> credentials = credentialRepository.findByClientIdAndActiveTrue(client.getId());

        return ClientSyncDto.builder()
                .id(client.getId())
                .name(client.getName())
                .active(client.isActive())
                .credentials(credentials.stream()
                        .map(this::mapCredentialToSync)
                        .collect(Collectors.toList()))
                .build();
    }

    private ClientSyncDto.CredentialSyncDto mapCredentialToSync(Credential credential) {
        return ClientSyncDto.CredentialSyncDto.builder()
                .id(credential.getId())
                .credentialType(credential.getCredentialType())
                .credentialValue(credential.getCredentialValue())
                .secretHash(credential.getSecretHash())
                .active(credential.isActive())
                .expiresAt(credential.getExpiresAt())
                .build();
    }

    private ServiceSyncDto mapServiceToSync(ServiceEntity service) {
        List<Route> routes = routeRepository.findActiveRoutesByService(service.getId());

        return ServiceSyncDto.builder()
                .id(service.getId())
                .name(service.getName())
                .version(service.getServiceVersion())
                .phase(service.getPhase())
                .basePath(service.getCollection() != null ? service.getCollection().getBasePath() : null)
                .fullPath(service.getFullPath())
                .k8sDeploymentName(service.getK8sDeploymentName())
                .k8sServiceName(service.getK8sServiceName())
                .minReplicas(service.getMinReplicas())
                .maxReplicas(service.getMaxReplicas())
                .active(service.isActive())
                .routes(routes.stream()
                        .map(this::mapRouteToSync)
                        .collect(Collectors.toList()))
                .build();
    }

    private RouteSyncDto mapRouteToSync(Route route) {
        List<Component> components = componentRepository.findByRouteIdOrderByOrderIndexAsc(route.getId());
        List<RouteFile> files = fileRepository.findByRouteId(route.getId());

        RouteSyncDto.RouteSyncDtoBuilder builder = RouteSyncDto.builder()
                .id(route.getId())
                .name(route.getName())
                .serviceId(route.getService() != null ? route.getService().getId() : null)
                .active(route.isActive());

        if (route.getFromEndpoint() != null) {
            builder.fromEndpoint(mapEndpointToSync(route.getFromEndpoint()));
        }
        if (route.getToEndpoint() != null) {
            builder.toEndpoint(mapEndpointToSync(route.getToEndpoint()));
        }

        builder.components(components.stream()
                .map(this::mapComponentToSync)
                .collect(Collectors.toList()));

        builder.files(files.stream()
                .map(this::mapFileToSync)
                .collect(Collectors.toList()));

        return builder.build();
    }

    private RouteSyncDto.EndpointSyncDto mapEndpointToSync(Endpoint endpoint) {
        return RouteSyncDto.EndpointSyncDto.builder()
                .id(endpoint.getId())
                .name(endpoint.getName())
                .uri(endpoint.getUri())
                .defaultRateLimit(endpoint.getDefaultRateLimit())
                .config(endpoint.getConfig())
                .build();
    }

    private RouteSyncDto.ComponentSyncDto mapComponentToSync(Component component) {
        return RouteSyncDto.ComponentSyncDto.builder()
                .id(component.getId())
                .name(component.getName())
                .componentType(component.getComponentType().name())
                .className(component.getClassName())
                .orderIndex(component.getOrderIndex())
                .config(component.getConfig())
                .build();
    }

    private RouteSyncDto.FileSyncDto mapFileToSync(RouteFile file) {
        return RouteSyncDto.FileSyncDto.builder()
                .id(file.getId())
                .filename(file.getFilename())
                .fileType(file.getFileType())
                .storagePath(file.getStoragePath())
                .contentHash(file.getContentHash())
                .content(file.getContent())
                .build();
    }

    private AccessSyncDto mapAccessToSync(ServiceAccess access) {
        return AccessSyncDto.builder()
                .id(access.getId())
                .clientId(access.getClient() != null ? access.getClient().getId() : null)
                .clientName(access.getClient() != null ? access.getClient().getName() : null)
                .serviceId(access.getService() != null ? access.getService().getId() : null)
                .serviceName(access.getService() != null ? access.getService().getName() : null)
                .serviceVersion(access.getService() != null ? access.getService().getServiceVersion() : null)
                .status(access.getStatus())
                .customRateLimit(access.getCustomRateLimit())
                .validFrom(access.getValidFrom())
                .validUntil(access.getValidUntil())
                .currentlyValid(access.isCurrentlyValid())
                .build();
    }
}
