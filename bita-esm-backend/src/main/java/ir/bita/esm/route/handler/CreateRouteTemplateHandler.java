package ir.bita.esm.route.handler;

import ir.bita.esm.route.command.CreateRouteTemplateCommand;
import ir.bita.esm.route.entity.EndpointTemplate;
import ir.bita.esm.route.entity.RouteTemplate;
import ir.bita.esm.route.repository.EndpointTemplateRepository;
import ir.bita.esm.route.repository.RouteTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for CreateRouteTemplateCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateRouteTemplateHandler {

    private final RouteTemplateRepository routeTemplateRepository;
    private final EndpointTemplateRepository endpointTemplateRepository;

    @Transactional
    public RouteTemplate handle(CreateRouteTemplateCommand command) {
        if (routeTemplateRepository.existsByNameAndDeletedFalse(command.getName())) {
            throw new IllegalArgumentException("Route template with name '" + command.getName() + "' already exists");
        }

        RouteTemplate.RouteTemplateBuilder builder = RouteTemplate.builder()
                .name(command.getName())
                .description(command.getDescription())
                .configSchema(command.getConfigSchema())
                .componentConfig(command.getComponentConfig())
                .category(command.getCategory());

        // Link from endpoint template
        if (command.getFromEndpointTemplateId() != null) {
            EndpointTemplate fromTemplate = endpointTemplateRepository
                    .findByIdAndDeletedFalse(command.getFromEndpointTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("From endpoint template not found"));
            builder.fromEndpointTemplate(fromTemplate);
        }

        // Link to endpoint template
        if (command.getToEndpointTemplateId() != null) {
            EndpointTemplate toTemplate = endpointTemplateRepository
                    .findByIdAndDeletedFalse(command.getToEndpointTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("To endpoint template not found"));
            builder.toEndpointTemplate(toTemplate);
        }

        RouteTemplate template = builder.build();
        template = routeTemplateRepository.save(template);

        log.info("Created route template: {} (ID: {})", template.getName(), template.getId());
        return template;
    }
}
