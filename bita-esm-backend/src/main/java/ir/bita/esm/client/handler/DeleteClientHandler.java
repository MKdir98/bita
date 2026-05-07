package ir.bita.esm.client.handler;

import ir.bita.common.event.ClientDeletedEvent;
import ir.bita.esm.client.command.DeleteClientCommand;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.client.repository.ClientRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for DeleteClientCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteClientHandler {

    private final ClientRepository clientRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void handle(DeleteClientCommand command) {
        Client client = clientRepository.findByIdAndDeletedFalse(command.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        // Soft delete
        client.softDelete();
        clientRepository.save(client);

        // Publish event
        ClientDeletedEvent event = ClientDeletedEvent.builder()
                .clientId(client.getId())
                .name(client.getName())
                .build();
        eventPublisher.publish(event);

        log.info("Soft deleted client: {} (ID: {})", client.getName(), client.getId());
    }
}
