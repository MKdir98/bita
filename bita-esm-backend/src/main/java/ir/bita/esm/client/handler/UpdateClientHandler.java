package ir.bita.esm.client.handler;

import ir.bita.common.dto.client.TagDto;
import ir.bita.common.event.ClientUpdatedEvent;
import ir.bita.esm.client.command.UpdateClientCommand;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.client.entity.Tag;
import ir.bita.esm.client.repository.ClientRepository;
import ir.bita.esm.client.repository.TagRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for UpdateClientCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateClientHandler {

    private final ClientRepository clientRepository;
    private final TagRepository tagRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public Client handle(UpdateClientCommand command) {
        Client client = clientRepository.findByIdAndDeletedFalse(command.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        // Check name uniqueness if changing
        if (command.getName() != null && !command.getName().equals(client.getName())) {
            if (clientRepository.existsByNameAndDeletedFalse(command.getName())) {
                throw new IllegalArgumentException("Client with name '" + command.getName() + "' already exists");
            }
            client.setName(command.getName());
        }

        // Update fields if provided
        if (command.getDescription() != null) {
            client.setDescription(command.getDescription());
        }
        if (command.getContactEmail() != null) {
            client.setContactEmail(command.getContactEmail());
        }
        if (command.getContactPhone() != null) {
            client.setContactPhone(command.getContactPhone());
        }
        if (command.getTags() != null) {
            // Clear existing tags and add new ones
            client.clearTags();
            for (TagDto tagDto : command.getTags()) {
                Tag tag = Tag.builder()
                        .key(tagDto.getKey())
                        .value(tagDto.getValue())
                        .client(client)
                        .build();
                client.getTags().add(tag);
            }
        }
        if (command.getActive() != null) {
            client.setActive(command.getActive());
        }

        // Save client
        client = clientRepository.save(client);

        // Publish event
        ClientUpdatedEvent event = ClientUpdatedEvent.builder()
                .clientId(client.getId())
                .name(client.getName())
                .build();
        eventPublisher.publish(event);

        log.info("Updated client: {} (ID: {})", client.getName(), client.getId());
        return client;
    }
}
