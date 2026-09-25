package ir.bita.esm.k8s;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.client.KubernetesClient;
import ir.bita.common.domain.VariableType;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.repository.ServiceGroovyConfigRepository;
import ir.bita.esm.service.entity.ServiceEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for deploying/undeploying services to Kubernetes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesDeployerService implements KubernetesDeployer {

    private final KubernetesClient kubernetesClient;
    private final K8sDeploymentBuilder deploymentBuilder;
    private final K8sServiceBuilder serviceBuilder;
    private final K8sHpaBuilder hpaBuilder;
    private final ServiceGroovyConfigRepository groovyConfigRepository;

    @Value("${kubernetes.namespace:bita-esb}")
    private String namespace;

    @Override
    public void deployService(ServiceEntity service) {
        log.info("Deploying service {} to Kubernetes namespace {}", service.getId(), namespace);

        try {
            // Generate resource names
            String deploymentName = service.generateK8sName();
            String serviceName = deploymentName + "-svc";

            // Resolve PORT variables from the service's GroovyTemplate (if any)
            List<GroovyTemplate.VariableMetadata> portVars = getPortVariables(service.getId());

            // 1. Create Deployment
            Deployment deployment = deploymentBuilder.build(service, deploymentName, portVars);
            kubernetesClient.apps().deployments()
                    .inNamespace(namespace)
                    .resource(deployment)
                    .create();
            log.info("Created Deployment: {}", deploymentName);

            // 2. Create Service
            io.fabric8.kubernetes.api.model.Service k8sService = serviceBuilder.build(service, serviceName, deploymentName, portVars);
            kubernetesClient.services()
                    .inNamespace(namespace)
                    .resource(k8sService)
                    .create();
            log.info("Created Service: {}", serviceName);

            // 3. Create HPA
            HorizontalPodAutoscaler hpa = hpaBuilder.build(service, deploymentName);
            kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(namespace)
                    .resource(hpa)
                    .create();
            log.info("Created HPA for: {}", deploymentName);

            // 4. Add Ingress path(s)
            addIngressPath(service, serviceName, portVars);

            log.info("Successfully deployed service {} to Kubernetes", service.getId());

        } catch (Exception e) {
            log.error("Failed to deploy service {} to Kubernetes: {}", service.getId(), e.getMessage());
            // Rollback any created resources
            try {
                undeployService(service);
            } catch (Exception rollbackEx) {
                log.error("Rollback failed: {}", rollbackEx.getMessage());
            }
            throw new RuntimeException("Kubernetes deployment failed", e);
        }
    }

    @Override
    public void undeployService(ServiceEntity service) {
        log.info("Undeploying service {} from Kubernetes namespace {}", service.getId(), namespace);

        String deploymentName = service.getK8sDeploymentName() != null
                ? service.getK8sDeploymentName()
                : service.generateK8sName();
        String serviceName = service.getK8sServiceName() != null
                ? service.getK8sServiceName()
                : deploymentName + "-svc";

        try {
            // 1. Remove Ingress path(s)
            List<GroovyTemplate.VariableMetadata> portVars = getPortVariables(service.getId());
            removeIngressPath(service, portVars);

            // 2. Delete HPA
            kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(namespace)
                    .withName(deploymentName + "-hpa")
                    .delete();
            log.info("Deleted HPA: {}-hpa", deploymentName);

            // 3. Delete Service
            kubernetesClient.services()
                    .inNamespace(namespace)
                    .withName(serviceName)
                    .delete();
            log.info("Deleted Service: {}", serviceName);

            // 4. Delete Deployment
            kubernetesClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .delete();
            log.info("Deleted Deployment: {}", deploymentName);

            log.info("Successfully undeployed service {} from Kubernetes", service.getId());

        } catch (Exception e) {
            log.error("Error undeploying service {}: {}", service.getId(), e.getMessage());
            throw new RuntimeException("Kubernetes undeployment failed", e);
        }
    }

    @Override
    public void updateScaling(ServiceEntity service) {
        log.info("Updating scaling for service {} in Kubernetes", service.getId());

        String deploymentName = service.getK8sDeploymentName();
        if (deploymentName == null) {
            throw new IllegalStateException("Service is not deployed to Kubernetes");
        }

        try {
            // Update HPA
            HorizontalPodAutoscaler hpa = hpaBuilder.build(service, deploymentName);
            kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(namespace)
                    .withName(deploymentName + "-hpa")
                    .patch(hpa);
            log.info("Updated HPA for: {}", deploymentName);

        } catch (Exception e) {
            log.error("Error updating scaling for service {}: {}", service.getId(), e.getMessage());
            throw new RuntimeException("Kubernetes HPA update failed", e);
        }
    }

    @Override
    public boolean isDeployed(ServiceEntity service) {
        if (service.getK8sDeploymentName() == null) {
            return false;
        }

        try {
            Deployment deployment = kubernetesClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(service.getK8sDeploymentName())
                    .get();
            return deployment != null;
        } catch (Exception e) {
            log.warn("Error checking deployment status: {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Ingress helpers
    // -------------------------------------------------------------------------

    private void addIngressPath(ServiceEntity service, String serviceName,
                                List<GroovyTemplate.VariableMetadata> portVars) {
        String path = service.getRoutablePath();
        if (path == null) {
            log.warn("Service has no path, skipping ingress configuration");
            return;
        }

        try {
            Ingress ingress = kubernetesClient.network().v1().ingresses()
                    .inNamespace(namespace)
                    .withName(KubernetesConfig.ESB_INGRESS_NAME)
                    .get();

            if (ingress == null) {
                log.warn("Ingress {} not found, creating new one", KubernetesConfig.ESB_INGRESS_NAME);
                ingress = createBaseIngress();
            }

            // Ensure the first rule has an HTTP block with a mutable path list
            if (ingress.getSpec().getRules() == null || ingress.getSpec().getRules().isEmpty()) {
                ingress = createBaseIngress();
            }
            IngressRule rule = ingress.getSpec().getRules().get(0);
            if (rule.getHttp() == null) {
                rule.setHttp(new io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressRuleValueBuilder()
                        .withPaths(new ArrayList<>())
                        .build());
            }

            // Add primary service path (existing 8080 logic)
            rule.getHttp().getPaths().add(buildIngressPath(path, serviceName, KubernetesConfig.ESB_CONTAINER_PORT));

            // Add an additional Ingress path for each PORT variable that has both a path and a
            // parseable defaultValue
            if (portVars != null) {
                for (GroovyTemplate.VariableMetadata portVar : portVars) {
                    if (portVar.getPath() == null || portVar.getPath().isBlank()) {
                        continue;
                    }
                    Integer portNum = parsePort(portVar.getDefaultValue());
                    if (portNum == null) {
                        continue;
                    }
                    rule.getHttp().getPaths().add(buildIngressPath(portVar.getPath(), serviceName, portNum));
                    log.info("Added ingress path for PORT var '{}': {} -> :{}", portVar.getName(), portVar.getPath(), portNum);
                }
            }

            kubernetesClient.network().v1().ingresses()
                    .inNamespace(namespace)
                    .resource(ingress)
                    .update();

            log.info("Added ingress path: {}", path);

        } catch (Exception e) {
            log.error("Error adding ingress path: {}", e.getMessage());
            throw new RuntimeException("Failed to add ingress path", e);
        }
    }

    private void removeIngressPath(ServiceEntity service, List<GroovyTemplate.VariableMetadata> portVars) {
        String path = service.getRoutablePath();
        if (path == null) {
            return;
        }

        try {
            Ingress ingress = kubernetesClient.network().v1().ingresses()
                    .inNamespace(namespace)
                    .withName(KubernetesConfig.ESB_INGRESS_NAME)
                    .get();

            if (ingress == null || ingress.getSpec().getRules() == null) {
                return;
            }

            // Collect all paths to remove: primary path + PORT variable paths
            List<String> pathsToRemove = new ArrayList<>();
            pathsToRemove.add(path);
            if (portVars != null) {
                for (GroovyTemplate.VariableMetadata portVar : portVars) {
                    if (portVar.getPath() != null && !portVar.getPath().isBlank()) {
                        pathsToRemove.add(portVar.getPath());
                    }
                }
            }

            for (IngressRule rule : ingress.getSpec().getRules()) {
                if (rule.getHttp() != null && rule.getHttp().getPaths() != null) {
                    rule.getHttp().getPaths().removeIf(p -> pathsToRemove.contains(p.getPath()));
                }
            }

            kubernetesClient.network().v1().ingresses()
                    .inNamespace(namespace)
                    .resource(ingress)
                    .update();

            log.info("Removed ingress path(s): {}", pathsToRemove);

        } catch (Exception e) {
            log.error("Error removing ingress path: {}", e.getMessage());
        }
    }

    private HTTPIngressPath buildIngressPath(String path, String serviceName, int port) {
        return new io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder()
                .withPath(path)
                .withPathType("Prefix")
                .withNewBackend()
                    .withNewService()
                        .withName(serviceName)
                        .withNewPort()
                            .withNumber(port)
                        .endPort()
                    .endService()
                .endBackend()
                .build();
    }

    private Ingress createBaseIngress() {
        return new io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder()
                .withNewMetadata()
                    .withName(KubernetesConfig.ESB_INGRESS_NAME)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withIngressClassName("nginx")
                    .addNewRule()
                        .withNewHttp()
                            .withPaths(new ArrayList<>())
                        .endHttp()
                    .endRule()
                .endSpec()
                .build();
    }

    // -------------------------------------------------------------------------
    // PORT variable resolution
    // -------------------------------------------------------------------------

    /**
     * Returns the PORT-typed {@link GroovyTemplate.VariableMetadata} entries for the given
     * service, or an empty list if no GroovyConfig / template is associated.
     */
    private List<GroovyTemplate.VariableMetadata> getPortVariables(Long serviceId) {
        return groovyConfigRepository.findByServiceId(serviceId)
                .map(c -> c.getGroovyTemplate().getVariables())
                .orElse(List.of())
                .stream()
                .filter(v -> VariableType.PORT == v.getType())
                .collect(Collectors.toList());
    }

    /** Returns the parsed port number, or {@code null} if the value is absent or not an integer. */
    private Integer parsePort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
