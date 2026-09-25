package ir.bita.esm.route.handler;

import ir.bita.esm.route.command.UpdateComponentTemplateCommand;
import ir.bita.esm.route.entity.ComponentTemplate;
import ir.bita.esm.route.repository.ComponentTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for UpdateComponentTemplateCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateComponentTemplateHandler {

    private final ComponentTemplateRepository repository;

    @Transactional
    public ComponentTemplate handle(UpdateComponentTemplateCommand command) {
        ComponentTemplate template = repository.findByIdAndDeletedFalse(command.getId())
                .orElseThrow(() -> new IllegalArgumentException("Component template not found with ID: " + command.getId()));

        // Check name uniqueness if name is being changed
        if (command.getName() != null && !command.getName().equals(template.getName())) {
            if (repository.existsByNameAndDeletedFalse(command.getName())) {
                throw new IllegalArgumentException("Component template with name '" + command.getName() + "' already exists");
            }
            template.setName(command.getName());
        }

        if (command.getDescription() != null) {
            template.setDescription(command.getDescription());
        }

        if (command.getComponentType() != null) {
            template.setComponentType(command.getComponentType());
        }

        if (command.getClassName() != null) {
            template.setClassName(command.getClassName());
        }

        if (command.getConfigSchema() != null) {
            template.setConfigSchema(command.getConfigSchema());
        }

        if (command.getGroovyCode() != null) {
            template.setGroovyCode(command.getGroovyCode());
        }

        template = repository.save(template);

        log.info("Updated component template: {} (ID: {})", template.getName(), template.getId());
        return template;
    }
}
