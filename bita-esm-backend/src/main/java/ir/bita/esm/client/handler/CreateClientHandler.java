package ir.bita.esm.client.handler;

import ir.bita.common.dto.client.TagDto;
import ir.bita.common.event.ClientCreatedEvent;
import ir.bita.esm.client.command.CreateClientCommand;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.client.entity.Tag;
import ir.bita.esm.client.repository.ClientRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/**
 * Handler for CreateClientCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateClientHandler {

    private final ClientRepository clientRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public Client handle(CreateClientCommand command) {
        // Validate unique name
        if (clientRepository.existsByNameAndDeletedFalse(command.getName())) {
            throw new IllegalArgumentException("Client with name '" + command.getName() + "' already exists");
        }

        // Build client entity
        Client client = Client.builder()
                .name(command.getName())
                .description(command.getDescription())
                .contactEmail(command.getContactEmail())
                .contactPhone(command.getContactPhone())
                .tags(new ArrayList<>())
                .build();

        // Add tags if provided
        if (command.getTags() != null) {
            for (TagDto tagDto : command.getTags()) {
                Tag tag = Tag.builder()
                        .key(tagDto.getKey())
                        .value(tagDto.getValue())
                        .client(client)
                        .build();
                client.getTags().add(tag);
            }
        }

        // Set parent if provided
        if (command.getParentId() != null) {
            Client parent = clientRepository.findByIdAndDeletedFalse(command.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent client not found"));
            client.setParent(parent);
        }

        // Save client
        client = clientRepository.save(client);

        // Publish event
        ClientCreatedEvent event = ClientCreatedEvent.builder()
                .clientId(client.getId())
                .name(client.getName())
                .build();
        eventPublisher.publish(event);

        log.info("Created client: {} (ID: {})", client.getName(), client.getId());
        return client;
    }
}
