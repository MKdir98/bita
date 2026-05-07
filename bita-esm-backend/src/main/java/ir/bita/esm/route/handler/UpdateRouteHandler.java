package ir.bita.esm.route.handler;

import ir.bita.common.event.RouteUpdatedEvent;
import ir.bita.esm.route.command.UpdateRouteCommand;
import ir.bita.esm.route.entity.Route;
import ir.bita.esm.route.repository.RouteRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for UpdateRouteCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateRouteHandler {

    private final RouteRepository routeRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public Route handle(UpdateRouteCommand command) {
        Route route = routeRepository.findByIdAndDeletedFalse(command.getRouteId())
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));

        // Check name uniqueness if changing
        if (command.getName() != null && !command.getName().equals(route.getName())) {
            if (routeRepository.existsByNameAndDeletedFalse(command.getName())) {
                throw new IllegalArgumentException("Route with name '" + command.getName() + "' already exists");
            }
            route.setName(command.getName());
        }

        if (command.getDescription() != null) {
            route.setDescription(command.getDescription());
        }

        if (command.getActive() != null) {
            route.setActive(command.getActive());
        }

        route = routeRepository.save(route);

        // Publish event
        RouteUpdatedEvent event = RouteUpdatedEvent.builder()
                .routeId(route.getId())
                .serviceId(route.getService() != null ? route.getService().getId() : null)
                .name(route.getName())
                .build();
        eventPublisher.publish(event);

        log.info("Updated route: {} (ID: {})", route.getName(), route.getId());
        return route;
    }
}
