package ir.bita.esm.service.handler;

import ir.bita.common.domain.ServicePhase;
import ir.bita.esm.k8s.KubernetesDeployer;
import ir.bita.esm.service.command.UpdateServiceScalingCommand;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for UpdateServiceScalingCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateServiceScalingHandler {

    private final ServiceRepository serviceRepository;
    private final KubernetesDeployer kubernetesDeployer;

    @Transactional
    public ServiceEntity handle(UpdateServiceScalingCommand command) {
        ServiceEntity service = serviceRepository.findByIdAndDeletedFalse(command.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));

        // Update scaling values if provided
        if (command.getMinReplicas() != null) {
            service.setMinReplicas(command.getMinReplicas());
        }
        if (command.getMaxReplicas() != null) {
            service.setMaxReplicas(command.getMaxReplicas());
        }
        if (command.getTargetCpuPercent() != null) {
            service.setTargetCpuPercent(command.getTargetCpuPercent());
        }

        // Validate min <= max
        if (service.getMinReplicas() > service.getMaxReplicas()) {
            throw new IllegalArgumentException("Minimum replicas cannot exceed maximum replicas");
        }

        // Save to DB
        service = serviceRepository.save(service);

        // Update K8s HPA if service is active
        if (service.getPhase() == ServicePhase.ACTIVE) {
            kubernetesDeployer.updateScaling(service);
            log.info("Updated K8s HPA for service {}", service.getId());
        }

        log.info("Updated scaling for service {}: min={}, max={}, targetCpu={}%",
                service.getId(), service.getMinReplicas(), service.getMaxReplicas(), service.getTargetCpuPercent());
        return service;
    }
}
