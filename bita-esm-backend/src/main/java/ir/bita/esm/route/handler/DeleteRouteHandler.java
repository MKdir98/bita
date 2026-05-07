package ir.bita.esm.route.handler;

import ir.bita.common.event.RouteDeletedEvent;
import ir.bita.esm.route.command.DeleteRouteCommand;
import ir.bita.esm.route.entity.Route;
import ir.bita.esm.route.repository.RouteRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for DeleteRouteCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteRouteHandler {

    private final RouteRepository routeRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void handle(DeleteRouteCommand command) {
        Route route = routeRepository.findByIdAndDeletedFalse(command.getRouteId())
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));

        // Soft delete
        route.softDelete();
        routeRepository.save(route);

        // Publish event
        RouteDeletedEvent event = RouteDeletedEvent.builder()
                .routeId(route.getId())
                .serviceId(route.getService() != null ? route.getService().getId() : null)
                .name(route.getName())
                .build();
        eventPublisher.publish(event);

        log.info("Soft deleted route: {} (ID: {})", route.getName(), route.getId());
    }
}
