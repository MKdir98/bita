package ir.bita.esm.service.handler;

import ir.bita.common.event.AccessRevokedEvent;
import ir.bita.esm.service.command.RevokeAccessCommand;
import ir.bita.esm.service.entity.ServiceAccess;
import ir.bita.esm.service.repository.ServiceAccessRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for RevokeAccessCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevokeAccessHandler {

    private final ServiceAccessRepository accessRepository;
    private final DomainEventPublisher eventPublisher;
    private final ir.bita.esm.route.service.EsbReloadNotifier esbNotifier;

    @Transactional
    public void handle(RevokeAccessCommand command) {
        ServiceAccess access = accessRepository.findById(command.getAccessId())
                .orElseThrow(() -> new IllegalArgumentException("Access not found"));

        // Revoke access
        access.revoke(command.getReason());
        accessRepository.save(access);

        // Publish event
        AccessRevokedEvent event = AccessRevokedEvent.builder()
                .accessId(access.getId())
                .clientId(access.getClient().getId())
                .serviceId(access.getService().getId())
                .reason(command.getReason())
                .build();
        eventPublisher.publish(event);
        esbNotifier.resyncAfterCommit(access.getService().getId());

        log.info("Revoked access {} for client {} to service {}", 
                access.getId(), access.getClient().getId(), access.getService().getId());
    }
}
