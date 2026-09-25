package ir.bita.esm.service.handler;

import ir.bita.common.event.AccessGrantedEvent;
import ir.bita.esm.client.repository.ClientRepository;
import ir.bita.esm.service.command.GrantAccessCommand;
import ir.bita.esm.service.entity.ServiceAccess;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.repository.ServiceAccessRepository;
import ir.bita.esm.service.repository.ServiceRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for GrantAccessCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrantAccessHandler {

    private final ServiceAccessRepository accessRepository;
    private final ServiceRepository serviceRepository;
    private final ClientRepository clientRepository;
    private final DomainEventPublisher eventPublisher;
    private final ir.bita.esm.route.service.EsbReloadNotifier esbNotifier;

    @Transactional
    public ServiceAccess handle(GrantAccessCommand command) {
        // Validate client exists
        var client = clientRepository.findByIdAndDeletedFalse(command.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        // Validate service exists
        ServiceEntity service = serviceRepository.findByIdAndDeletedFalse(command.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));

        // Check for existing access
        var existingAccess = accessRepository.findByClientIdAndServiceId(
                command.getClientId(), command.getServiceId());
        
        if (existingAccess.isPresent()) {
            ServiceAccess access = existingAccess.get();
            // Reactivate if previously revoked/expired
            access.reactivate();
            access.setCustomRateLimit(command.getCustomRateLimit());
            access.setValidFrom(command.getValidFrom());
            access.setValidUntil(command.getValidUntil());
            access.setGrantReason(command.getGrantReason());
            access = accessRepository.save(access);
            esbNotifier.resyncAfterCommit(service.getId());

            log.info("Reactivated access for client {} to service {}",
                    command.getClientId(), command.getServiceId());
            return access;
        }

        // Create new access
        ServiceAccess access = ServiceAccess.builder()
                .client(client)
                .service(service)
                .customRateLimit(command.getCustomRateLimit())
                .validFrom(command.getValidFrom())
                .validUntil(command.getValidUntil())
                .grantReason(command.getGrantReason())
                .build();

        access = accessRepository.save(access);

        // Publish event
        AccessGrantedEvent event = AccessGrantedEvent.builder()
                .accessId(access.getId())
                .clientId(client.getId())
                .serviceId(service.getId())
                .build();
        eventPublisher.publish(event);
        esbNotifier.resyncAfterCommit(access.getService().getId());

        log.info("Granted access for client {} to service {}", 
                command.getClientId(), command.getServiceId());
        return access;
    }
}
