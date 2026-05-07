package ir.bita.esm.k8s;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import ir.bita.esm.service.entity.ServiceEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builder for Kubernetes Service resources.
 */
@Component
public class K8sServiceBuilder {

    /**
     * Builds a Kubernetes Service for the given service entity.
     */
    public Service build(ServiceEntity serviceEntity, String serviceName, String deploymentName) {
        Map<String, String> labels = Map.of(
                "app", "esb-core",
                "service-id", String.valueOf(serviceEntity.getId()),
                "version", serviceEntity.getServiceVersion()
        );

        Map<String, String> selector = Map.of(
                "app", "esb-core",
                "service-id", String.valueOf(serviceEntity.getId())
        );

        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(serviceName)
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withType("ClusterIP")
                    .withSelector(selector)
                    .addNewPort()
                        .withName("http")
                        .withPort(KubernetesConfig.ESB_CONTAINER_PORT)
                        .withTargetPort(new io.fabric8.kubernetes.api.model.IntOrString(KubernetesConfig.ESB_CONTAINER_PORT))
                        .withProtocol("TCP")
                    .endPort()
                .endSpec()
                .build();
    }
}
