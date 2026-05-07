package ir.bita.esm.service.handler;

import ir.bita.common.domain.ServicePhase;
import ir.bita.common.event.ServiceCreatedEvent;
import ir.bita.esm.service.command.CreateServiceCommand;
import ir.bita.esm.service.entity.ServiceCollection;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.repository.ServiceCollectionRepository;
import ir.bita.esm.service.repository.ServiceRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for CreateServiceCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateServiceHandler {

    private final ServiceRepository serviceRepository;
    private final ServiceCollectionRepository collectionRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public ServiceEntity handle(CreateServiceCommand command) {
        // Validate min <= max replicas
        if (command.getMinReplicas() > command.getMaxReplicas()) {
            throw new IllegalArgumentException("Minimum replicas cannot exceed maximum replicas");
        }

        // Find collection
        ServiceCollection collection = collectionRepository.findByIdAndDeletedFalse(command.getCollectionId())
                .orElseThrow(() -> new IllegalArgumentException("Service collection not found"));

        // Validate unique version within collection
        if (serviceRepository.existsByCollectionIdAndServiceVersionAndDeletedFalse(
                command.getCollectionId(), command.getVersion())) {
            throw new IllegalArgumentException("Version '" + command.getVersion() + 
                    "' already exists in collection '" + collection.getName() + "'");
        }

        // Create service
        ServiceEntity service = ServiceEntity.builder()
                .collection(collection)
                .name(command.getName())
                .serviceVersion(command.getVersion())
                .description(command.getDescription())
                .phase(ServicePhase.DRAFT)
                .minReplicas(command.getMinReplicas())
                .maxReplicas(command.getMaxReplicas())
                .targetCpuPercent(command.getTargetCpuPercent())
                .build();

        service = serviceRepository.save(service);

        // Publish event
        ServiceCreatedEvent event = ServiceCreatedEvent.builder()
                .serviceId(service.getId())
                .collectionId(collection.getId())
                .name(service.getName())
                .version(service.getServiceVersion())
                .build();
        eventPublisher.publish(event);

        log.info("Created service: {} v{} (ID: {})", service.getName(), service.getServiceVersion(), service.getId());
        return service;
    }
}
