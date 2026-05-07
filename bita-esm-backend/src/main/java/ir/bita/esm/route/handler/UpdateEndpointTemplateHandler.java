package ir.bita.esm.route.handler;

import ir.bita.esm.route.command.UpdateEndpointTemplateCommand;
import ir.bita.esm.route.entity.EndpointTemplate;
import ir.bita.esm.route.repository.EndpointTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for UpdateEndpointTemplateCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateEndpointTemplateHandler {

    private final EndpointTemplateRepository repository;

    @Transactional
    public EndpointTemplate handle(UpdateEndpointTemplateCommand command) {
        EndpointTemplate template = repository.findByIdAndDeletedFalse(command.getId())
                .orElseThrow(() -> new IllegalArgumentException("Endpoint template not found with ID: " + command.getId()));

        // Check name uniqueness if name is being changed
        if (command.getName() != null && !command.getName().equals(template.getName())) {
            if (repository.existsByNameAndDeletedFalse(command.getName())) {
                throw new IllegalArgumentException("Endpoint template with name '" + command.getName() + "' already exists");
            }
            template.setName(command.getName());
        }

        if (command.getDescription() != null) {
            template.setDescription(command.getDescription());
        }

        if (command.getCamelYaml() != null) {
            template.setCamelYaml(command.getCamelYaml());
        }
        if (command.getAttachmentIds() != null) {
            template.setAttachmentIds(new java.util.ArrayList<>(command.getAttachmentIds()));
        }

        if (command.getConfigSchema() != null) {
            template.setConfigSchema(command.getConfigSchema());
        }

        if (command.getDefaultRateLimit() != null) {
            template.setDefaultRateLimit(command.getDefaultRateLimit());
        }

        if (command.getCategory() != null) {
            template.setCategory(command.getCategory());
        }

        template = repository.save(template);

        log.info("Updated endpoint template: {} (ID: {})", template.getName(), template.getId());
        return template;
    }
}
