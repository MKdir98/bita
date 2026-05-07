package ir.bita.esm.route.handler;

import ir.bita.esm.route.command.CreateEndpointTemplateCommand;
import ir.bita.esm.route.entity.EndpointTemplate;
import ir.bita.esm.route.repository.EndpointTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for CreateEndpointTemplateCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateEndpointTemplateHandler {

    private final EndpointTemplateRepository repository;

    @Transactional
    public EndpointTemplate handle(CreateEndpointTemplateCommand command) {
        if (repository.existsByNameAndDeletedFalse(command.getName())) {
            throw new IllegalArgumentException("Endpoint template with name '" + command.getName() + "' already exists");
        }

        EndpointTemplate template = EndpointTemplate.builder()
                .name(command.getName())
                .description(command.getDescription())
                .camelYaml(command.getCamelYaml())
                .attachmentIds(command.getAttachmentIds() != null ? new java.util.ArrayList<>(command.getAttachmentIds()) : new java.util.ArrayList<>())
                .configSchema(command.getConfigSchema())
                .defaultRateLimit(command.getDefaultRateLimit())
                .category(command.getCategory())
                .build();

        template = repository.save(template);

        log.info("Created endpoint template: {} (ID: {})", template.getName(), template.getId());
        return template;
    }
}
