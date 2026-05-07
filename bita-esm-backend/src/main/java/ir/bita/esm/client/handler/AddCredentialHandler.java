package ir.bita.esm.client.handler;

import ir.bita.common.event.CredentialAddedEvent;
import ir.bita.esm.client.command.AddCredentialCommand;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.client.entity.Credential;
import ir.bita.esm.client.repository.ClientRepository;
import ir.bita.esm.client.repository.CredentialRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for AddCredentialCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddCredentialHandler {

    private final ClientRepository clientRepository;
    private final CredentialRepository credentialRepository;
    private final DomainEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Credential handle(AddCredentialCommand command) {
        Client client = clientRepository.findByIdAndDeletedFalse(command.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        // Check for duplicate credential
        if (credentialRepository.existsByCredentialTypeAndCredentialValue(
                command.getCredentialType(), command.getCredentialValue())) {
            throw new IllegalArgumentException("Credential already exists");
        }

        // Build credential
        Credential credential = Credential.builder()
                .client(client)
                .credentialType(command.getCredentialType())
                .credentialValue(command.getCredentialValue())
                .description(command.getDescription())
                .expiresAt(command.getExpiresAt())
                .certificateContent(command.getCertificateContent())
                .build();

        // Hash secret if provided
        if (command.getSecret() != null) {
            credential.setSecretHash(passwordEncoder.encode(command.getSecret()));
        }

        // Save credential
        credential = credentialRepository.save(credential);

        // Publish event
        CredentialAddedEvent event = CredentialAddedEvent.builder()
                .clientId(client.getId())
                .credentialId(credential.getId())
                .credentialType(command.getCredentialType())
                .build();
        eventPublisher.publish(event);

        log.info("Added credential {} to client {} (ID: {})", 
                command.getCredentialType(), client.getName(), credential.getId());
        return credential;
    }
}
