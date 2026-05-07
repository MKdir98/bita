package ir.bita.esm.service.handler;

import ir.bita.common.domain.ServicePhase;
import ir.bita.common.event.ServicePhaseChangedEvent;
import ir.bita.esm.k8s.KubernetesDeployer;
import ir.bita.esm.service.command.ChangeServicePhaseCommand;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.repository.ServiceRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for ChangeServicePhaseCommand.
 * Triggers K8s deployment/undeployment based on phase change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeServicePhaseHandler {

    private final ServiceRepository serviceRepository;
    private final KubernetesDeployer kubernetesDeployer;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public ServiceEntity handle(ChangeServicePhaseCommand command) {
        ServiceEntity service = serviceRepository.findByIdAndDeletedFalse(command.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));

        ServicePhase oldPhase = service.getPhase();
        ServicePhase newPhase = command.getNewPhase();

        // Validate phase transition
        validatePhaseTransition(oldPhase, newPhase);

        // Handle K8s operations for deployable phases (TEST, ACTIVE)
        boolean oldDeployed = oldPhase.isDeployed();
        boolean newDeployed = newPhase.isDeployed();
        if (newDeployed && !oldDeployed) {
            // Deploy to Kubernetes
            kubernetesDeployer.deployService(service);
            
            // Set K8s resource names
            service.setK8sDeploymentName(service.generateK8sName());
            service.setK8sServiceName(service.generateK8sName() + "-svc");
        } else if (oldDeployed && !newDeployed) {
            // Undeploy from Kubernetes
            kubernetesDeployer.undeployService(service);
            
            // Clear K8s resource names
            service.setK8sDeploymentName(null);
            service.setK8sServiceName(null);
        }

        // Update phase
        service.setPhase(newPhase);
        service = serviceRepository.save(service);

        // Publish event
        ServicePhaseChangedEvent event = ServicePhaseChangedEvent.builder()
                .serviceId(service.getId())
                .oldPhase(oldPhase)
                .newPhase(newPhase)
                .build();
        eventPublisher.publish(event);

        log.info("Changed service {} phase from {} to {}", service.getId(), oldPhase, newPhase);
        return service;
    }

    private void validatePhaseTransition(ServicePhase from, ServicePhase to) {
        if (from == to) {
            throw new IllegalArgumentException("Service is already in phase " + from);
        }

        // Use the built-in transition validation
        if (!from.canTransitionTo(to)) {
            throw new IllegalArgumentException("Cannot transition from " + from + " to " + to);
        }

        log.debug("Phase transition {} -> {} is valid", from, to);
    }
}
