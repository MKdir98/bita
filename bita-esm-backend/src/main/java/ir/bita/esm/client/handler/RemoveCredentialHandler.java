package ir.bita.esm.client.handler;

import ir.bita.common.event.CredentialRemovedEvent;
import ir.bita.esm.client.command.RemoveCredentialCommand;
import ir.bita.esm.client.entity.Credential;
import ir.bita.esm.client.repository.ClientRepository;
import ir.bita.esm.client.repository.CredentialRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for RemoveCredentialCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemoveCredentialHandler {

    private final ClientRepository clientRepository;
    private final CredentialRepository credentialRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void handle(RemoveCredentialCommand command) {
        // Verify client exists
        if (!clientRepository.existsById(command.getClientId())) {
            throw new IllegalArgumentException("Client not found");
        }

        Credential credential = credentialRepository.findByIdAndClientId(
                command.getCredentialId(), command.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));

        // Delete credential
        credentialRepository.delete(credential);

        // Publish event
        CredentialRemovedEvent event = CredentialRemovedEvent.builder()
                .clientId(command.getClientId())
                .credentialId(command.getCredentialId())
                .credentialType(credential.getCredentialType())
                .build();
        eventPublisher.publish(event);

        log.info("Removed credential {} from client ID {}", 
                command.getCredentialId(), command.getClientId());
    }
}
