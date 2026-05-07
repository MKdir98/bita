package ir.bita.esm.service.query;

import ir.bita.common.domain.ServicePhase;
import ir.bita.esm.service.dto.ServiceAccessResponse;
import ir.bita.esm.service.dto.ServiceCollectionResponse;
import ir.bita.esm.service.dto.ServiceResponse;
import ir.bita.esm.service.entity.ServiceAccess;
import ir.bita.esm.service.entity.ServiceCollection;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.repository.ServiceAccessRepository;
import ir.bita.esm.service.repository.ServiceCollectionRepository;
import ir.bita.esm.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Query service for Service read operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceQueryService {

    private final ServiceCollectionRepository collectionRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceAccessRepository accessRepository;

    // Collection queries

    public ServiceCollectionResponse getCollection(Long id) {
        ServiceCollection collection = collectionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found"));
        return mapCollectionToResponse(collection);
    }

    public Page<ServiceCollectionResponse> listCollections(Pageable pageable) {
        return collectionRepository.findByDeletedFalse(pageable)
                .map(this::mapCollectionToResponse);
    }

    public Page<ServiceCollectionResponse> searchCollections(String query, Pageable pageable) {
        return collectionRepository.findByNameContainingIgnoreCaseAndDeletedFalse(query, pageable)
                .map(this::mapCollectionToResponse);
    }

    // Service queries

    public ServiceResponse getService(Long id) {
        ServiceEntity service = serviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));
        return mapServiceToResponse(service);
    }

    public Page<ServiceResponse> listServices(Pageable pageable) {
        return serviceRepository.findByDeletedFalse(pageable)
                .map(this::mapServiceToResponse);
    }

    public Page<ServiceResponse> listServicesByPhase(ServicePhase phase, Pageable pageable) {
        return serviceRepository.findByPhaseAndDeletedFalse(phase, pageable)
                .map(this::mapServiceToResponse);
    }

    public List<ServiceResponse> getServicesInCollection(Long collectionId) {
        return serviceRepository.findByCollectionIdAndDeletedFalse(collectionId).stream()
                .map(this::mapServiceToResponse)
                .collect(Collectors.toList());
    }

    public Page<ServiceResponse> searchServices(String query, Pageable pageable) {
        return serviceRepository.findByNameContainingIgnoreCaseAndDeletedFalse(query, pageable)
                .map(this::mapServiceToResponse);
    }

    // Access queries

    public Page<ServiceAccessResponse> listAccess(Pageable pageable) {
        return accessRepository.findAll(pageable)
                .map(this::mapAccessToResponse);
    }

    public List<ServiceAccessResponse> getAccessByClient(Long clientId) {
        return accessRepository.findByClientId(clientId).stream()
                .map(this::mapAccessToResponse)
                .collect(Collectors.toList());
    }

    public List<ServiceAccessResponse> getAccessByService(Long serviceId) {
        return accessRepository.findByServiceId(serviceId).stream()
                .map(this::mapAccessToResponse)
                .collect(Collectors.toList());
    }

    // Statistics

    public long countCollections() {
        return collectionRepository.countByDeletedFalse();
    }

    public long countServicesByPhase(ServicePhase phase) {
        return serviceRepository.countByPhaseAndDeletedFalse(phase);
    }

    // Mappers

    private ServiceCollectionResponse mapCollectionToResponse(ServiceCollection collection) {
        return ServiceCollectionResponse.builder()
                .id(collection.getId())
                .name(collection.getName())
                .basePath(collection.getBasePath())
                .description(collection.getDescription())
                .active(collection.isActive())
                .serviceCount(collection.getServices() != null ? collection.getServices().size() : 0)
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .build();
    }

    private ServiceResponse mapServiceToResponse(ServiceEntity service) {
        ServiceResponse.ServiceResponseBuilder builder = ServiceResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .version(service.getServiceVersion())
                .description(service.getDescription())
                .phase(service.getPhase())
                .minReplicas(service.getMinReplicas())
                .maxReplicas(service.getMaxReplicas())
                .targetCpuPercent(service.getTargetCpuPercent())
                .k8sDeploymentName(service.getK8sDeploymentName())
                .k8sServiceName(service.getK8sServiceName())
                .deployed(service.isDeployed())
                .active(service.isActive())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt());

        if (service.getCollection() != null) {
            builder.collectionId(service.getCollection().getId())
                    .collectionName(service.getCollection().getName())
                    .basePath(service.getCollection().getBasePath())
                    .fullPath(service.getRoutablePath());
        }

        return builder.build();
    }

    private ServiceAccessResponse mapAccessToResponse(ServiceAccess access) {
        ServiceAccessResponse.ServiceAccessResponseBuilder builder = ServiceAccessResponse.builder()
                .id(access.getId())
                .status(access.getStatus())
                .customRateLimit(access.getCustomRateLimit())
                .validFrom(access.getValidFrom())
                .validUntil(access.getValidUntil())
                .grantReason(access.getGrantReason())
                .revokeReason(access.getRevokeReason())
                .currentlyValid(access.isCurrentlyValid())
                .createdAt(access.getCreatedAt())
                .updatedAt(access.getUpdatedAt());

        if (access.getClient() != null) {
            builder.clientId(access.getClient().getId())
                    .clientName(access.getClient().getName());
        }

        if (access.getService() != null) {
            builder.serviceId(access.getService().getId())
                    .serviceName(access.getService().getName())
                    .serviceVersion(access.getService().getServiceVersion());
        }

        return builder.build();
    }
}
