package ir.bita.esm.route.handler;

import ir.bita.common.event.RouteCreatedEvent;
import ir.bita.esm.route.command.CreateRouteCommand;
import ir.bita.esm.route.entity.*;
import ir.bita.esm.route.repository.*;
import ir.bita.esm.route.service.TemplateResolverService;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.repository.ServiceRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for CreateRouteCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateRouteHandler {

    private final RouteRepository routeRepository;
    private final RouteTemplateRepository routeTemplateRepository;
    private final EndpointTemplateRepository endpointTemplateRepository;
    private final ComponentTemplateRepository componentTemplateRepository;
    private final EndpointRepository endpointRepository;
    private final ComponentRepository componentRepository;
    private final ServiceRepository serviceRepository;
    private final TemplateResolverService templateResolver;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public Route handle(CreateRouteCommand command) {
        // Validate unique name
        if (routeRepository.existsByNameAndDeletedFalse(command.getName())) {
            throw new IllegalArgumentException("Route with name '" + command.getName() + "' already exists");
        }

        // Get service
        ServiceEntity service = serviceRepository.findByIdAndDeletedFalse(command.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));

        // Get template if provided
        RouteTemplate template = null;
        if (command.getTemplateId() != null) {
            template = routeTemplateRepository.findByIdAndDeletedFalse(command.getTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("Route template not found"));
        }

        // Create from endpoint
        Endpoint fromEndpoint = createEndpoint(command.getFromEndpoint());
        fromEndpoint = endpointRepository.save(fromEndpoint);

        // Create to endpoint
        Endpoint toEndpoint = createEndpoint(command.getToEndpoint());
        toEndpoint = endpointRepository.save(toEndpoint);

        // Create route
        Route route = Route.builder()
                .service(service)
                .template(template)
                .name(command.getName())
                .description(command.getDescription())
                .fromEndpoint(fromEndpoint)
                .toEndpoint(toEndpoint)
                .build();

        route = routeRepository.save(route);

        // Create components
        if (command.getComponents() != null && !command.getComponents().isEmpty()) {
            List<Component> components = new ArrayList<>();
            int orderIndex = 0;
            for (CreateRouteCommand.ComponentConfig config : command.getComponents()) {
                Component component = createComponent(config, route, orderIndex++);
                components.add(componentRepository.save(component));
            }
        }

        // Publish event
        RouteCreatedEvent event = RouteCreatedEvent.builder()
                .routeId(route.getId())
                .serviceId(service.getId())
                .name(route.getName())
                .build();
        eventPublisher.publish(event);

        log.info("Created route: {} (ID: {}) for service {}", route.getName(), route.getId(), service.getId());
        return route;
    }

    private Endpoint createEndpoint(CreateRouteCommand.EndpointConfig config) {
        EndpointTemplate template = null;
        String uri = config.getUri();

        if (config.getTemplateId() != null) {
            template = endpointTemplateRepository.findByIdAndDeletedFalse(config.getTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("Endpoint template not found"));

            // Resolve URI from template camel_yaml
            if (config.getConfig() != null && template.getCamelYaml() != null) {
                String resolved = templateResolver.extractAndResolveUriFromCamelYaml(template.getCamelYaml(), config.getConfig());
                if (resolved != null) {
                    uri = resolved;
                }
            }
        }

        return Endpoint.builder()
                .template(template)
                .name(config.getName())
                .description(config.getDescription())
                .uri(uri)
                .config(config.getConfig())
                .defaultRateLimit(config.getDefaultRateLimit())
                .build();
    }

    private Component createComponent(CreateRouteCommand.ComponentConfig config, Route route, int orderIndex) {
        ComponentTemplate template = null;

        if (config.getTemplateId() != null) {
            template = componentTemplateRepository.findByIdAndDeletedFalse(config.getTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("Component template not found"));
        }

        return Component.builder()
                .template(template)
                .route(route)
                .name(config.getName())
                .description(config.getDescription())
                .componentType(config.getComponentType())
                .className(config.getClassName())
                .orderIndex(orderIndex)
                .config(config.getConfig())
                .build();
    }
}
