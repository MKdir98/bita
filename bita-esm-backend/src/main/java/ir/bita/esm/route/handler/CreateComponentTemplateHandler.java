package ir.bita.esm.route.handler;

import ir.bita.esm.route.command.CreateComponentTemplateCommand;
import ir.bita.esm.route.entity.ComponentTemplate;
import ir.bita.esm.route.repository.ComponentTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for CreateComponentTemplateCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateComponentTemplateHandler {

    private final ComponentTemplateRepository repository;

    @Transactional
    public ComponentTemplate handle(CreateComponentTemplateCommand command) {
        if (repository.existsByNameAndDeletedFalse(command.getName())) {
            throw new IllegalArgumentException("Component template with name '" + command.getName() + "' already exists");
        }

        ComponentTemplate template = ComponentTemplate.builder()
                .name(command.getName())
                .description(command.getDescription())
                .componentType(command.getComponentType())
                .className(command.getClassName())
                .configSchema(command.getConfigSchema())
                .category(command.getCategory())
                .groovyCode(command.getGroovyCode())
                .build();

        template = repository.save(template);

        log.info("Created component template: {} (ID: {})", template.getName(), template.getId());
        return template;
    }
}
