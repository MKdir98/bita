package ir.bita.esm.sync.service;

import ir.bita.common.domain.CredentialType;
import ir.bita.common.domain.ServicePhase;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.client.entity.Credential;
import ir.bita.esm.client.repository.ClientRepository;
import ir.bita.esm.client.repository.CredentialRepository;
import ir.bita.esm.route.entity.ServiceGroovyConfig;
import ir.bita.esm.route.repository.ServiceGroovyConfigRepository;
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
import java.util.Objects;
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
    private final ServiceGroovyConfigRepository groovyConfigRepository;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    public FullSyncDataDto getFullSyncData() {
        log.info("Generating full sync data");

        List<ClientSyncDto> clients = getAllClientsWithCredentials();
        List<ServiceSyncDto> services = getAllServices();
        List<AccessSyncDto> accessRules = getAllAccessRules();

        FullSyncDataDto.Statistics stats = FullSyncDataDto.Statistics.builder()
                .totalClients(clientRepository.countByDeletedFalse())
                .totalServices(serviceRepository.count())
                .activeServices(serviceRepository.countByPhaseAndDeletedFalse(ServicePhase.ACTIVE))
                .totalRoutes(0L)
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

    public List<ClientSyncDto> getAllClientsWithCredentials() {
        return clientRepository.findByDeletedFalse(Pageable.unpaged()).stream()
                .map(this::mapClientToSync)
                .collect(Collectors.toList());
    }

    public List<ServiceSyncDto> getAllServices() {
        return serviceRepository.findByDeletedFalse(Pageable.unpaged()).stream()
                .map(this::mapServiceToSync)
                .collect(Collectors.toList());
    }

    /** @deprecated Routes are now Groovy scripts — use getAllServices(). */
    @Deprecated
    public List<ServiceSyncDto> getAllServicesWithRoutes() {
        return getAllServices();
    }

    public List<AccessSyncDto> getAllAccessRules() {
        return accessRepository.findAll(Pageable.unpaged()).stream()
                .map(this::mapAccessToSync)
                .collect(Collectors.toList());
    }

    /**
     * Returns assembled Groovy script + variable values for a single ESB pod.
     * Called by the ESB at startup via GET /internal/v1/sync/services/{serviceId}/config.
     */
    public ServiceConfigDto getServiceConfig(Long serviceId) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found: " + serviceId));

        ServiceGroovyConfig groovyConfig = groovyConfigRepository.findByServiceId(serviceId)
                .orElse(null);

        String assembledScript = groovyConfig != null ? groovyConfig.getAssembledScript() : null;
        java.util.Map<String, Object> variableValues = groovyConfig != null ? groovyConfig.getVariableValues() : null;

        List<String> clientCertPems = accessRepository.findAll(Pageable.unpaged()).stream()
                .filter(a -> a.getService() != null && serviceId.equals(a.getService().getId()))
                .filter(ServiceAccess::isCurrentlyValid)
                .map(ServiceAccess::getClient)
                .filter(Objects::nonNull)
                .flatMap(c -> credentialRepository.findByClientIdAndActiveTrue(c.getId()).stream())
                .filter(cred -> CredentialType.X509_CERTIFICATE == cred.getCredentialType())
                .map(Credential::getCredentialValue)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        return ServiceConfigDto.builder()
                .serviceId(serviceId)
                .name(service.getName())
                .assembledScript(assembledScript)
                .variableValues(variableValues)
                .authorizedClientCertPems(clientCertPems)
                .sandboxed(groovyConfig != null && groovyConfig.getGroovyTemplate() != null
                        && groovyConfig.getGroovyTemplate().isModelAuthored())
                .build();
    }

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
