package ir.bita.esm.route.handler;

import ir.bita.esm.route.command.UpdateRouteTemplateCommand;
import ir.bita.esm.route.entity.EndpointTemplate;
import ir.bita.esm.route.entity.RouteTemplate;
import ir.bita.esm.route.repository.EndpointTemplateRepository;
import ir.bita.esm.route.repository.RouteTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for UpdateRouteTemplateCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateRouteTemplateHandler {

    private final RouteTemplateRepository repository;
    private final EndpointTemplateRepository endpointTemplateRepository;

    @Transactional
    public RouteTemplate handle(UpdateRouteTemplateCommand command) {
        RouteTemplate template = repository.findByIdAndDeletedFalse(command.getId())
                .orElseThrow(() -> new IllegalArgumentException("Route template not found with ID: " + command.getId()));

        // Check name uniqueness if name is being changed
        if (command.getName() != null && !command.getName().equals(template.getName())) {
            if (repository.existsByNameAndDeletedFalse(command.getName())) {
                throw new IllegalArgumentException("Route template with name '" + command.getName() + "' already exists");
            }
            template.setName(command.getName());
        }

        if (command.getDescription() != null) {
            template.setDescription(command.getDescription());
        }

        if (command.getFromEndpointTemplateId() != null) {
            EndpointTemplate fromTemplate = endpointTemplateRepository.findByIdAndDeletedFalse(command.getFromEndpointTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("From endpoint template not found with ID: " + command.getFromEndpointTemplateId()));
            template.setFromEndpointTemplate(fromTemplate);
        }

        if (command.getToEndpointTemplateId() != null) {
            EndpointTemplate toTemplate = endpointTemplateRepository.findByIdAndDeletedFalse(command.getToEndpointTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("To endpoint template not found with ID: " + command.getToEndpointTemplateId()));
            template.setToEndpointTemplate(toTemplate);
        }

        if (command.getConfigSchema() != null) {
            template.setConfigSchema(command.getConfigSchema());
        }

        if (command.getComponentConfig() != null) {
            template.setComponentConfig(command.getComponentConfig());
        }

        if (command.getCategory() != null) {
            template.setCategory(command.getCategory());
        }

        template = repository.save(template);

        log.info("Updated route template: {} (ID: {})", template.getName(), template.getId());
        return template;
    }
}
