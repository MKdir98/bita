package ir.bita.esm.k8s;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import ir.bita.esm.service.entity.ServiceEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builder for Kubernetes Deployment resources.
 */
@Component
public class K8sDeploymentBuilder {

    /**
     * Builds a Kubernetes Deployment for the given service.
     */
    public Deployment build(ServiceEntity service, String deploymentName) {
        Map<String, String> labels = Map.of(
                "app", "esb-core",
                "service-id", String.valueOf(service.getId()),
                "version", service.getServiceVersion()
        );

        return new DeploymentBuilder()
                .withNewMetadata()
                    .withName(deploymentName)
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(service.getMinReplicas())
                    .withNewSelector()
                        .withMatchLabels(labels)
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .withLabels(labels)
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("esb-core")
                                .withImage(KubernetesConfig.ESB_CORE_IMAGE)
                                .withImagePullPolicy("Always")
                                .addNewPort()
                                    .withContainerPort(KubernetesConfig.ESB_CONTAINER_PORT)
                                    .withProtocol("TCP")
                                .endPort()
                                .addNewEnv()
                                    .withName("SERVICE_ID")
                                    .withValue(String.valueOf(service.getId()))
                                .endEnv()
                                .addNewEnv()
                                    .withName("SERVICE_VERSION")
                                    .withValue(service.getServiceVersion())
                                .endEnv()
                                .addNewEnv()
                                    .withName("JAVA_OPTS")
                                    .withValue("-Xms256m -Xmx512m")
                                .endEnv()
                                .withNewResources()
                                    .addToRequests("cpu", new io.fabric8.kubernetes.api.model.Quantity("100m"))
                                    .addToRequests("memory", new io.fabric8.kubernetes.api.model.Quantity("256Mi"))
                                    .addToLimits("cpu", new io.fabric8.kubernetes.api.model.Quantity("500m"))
                                    .addToLimits("memory", new io.fabric8.kubernetes.api.model.Quantity("512Mi"))
                                .endResources()
                                .withNewReadinessProbe()
                                    .withNewHttpGet()
                                        .withPath("/health/ready")
                                        .withNewPort(KubernetesConfig.ESB_CONTAINER_PORT)
                                    .endHttpGet()
                                    .withInitialDelaySeconds(10)
                                    .withPeriodSeconds(5)
                                    .withTimeoutSeconds(3)
                                .endReadinessProbe()
                                .withNewLivenessProbe()
                                    .withNewHttpGet()
                                        .withPath("/health/live")
                                        .withNewPort(KubernetesConfig.ESB_CONTAINER_PORT)
                                    .endHttpGet()
                                    .withInitialDelaySeconds(30)
                                    .withPeriodSeconds(10)
                                    .withTimeoutSeconds(5)
                                .endLivenessProbe()
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
    }
}
