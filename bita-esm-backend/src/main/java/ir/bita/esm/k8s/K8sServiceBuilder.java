package ir.bita.esm.k8s;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.service.entity.ServiceEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builder for Kubernetes Service resources.
 */
@Component
public class K8sServiceBuilder {

    /**
     * Builds a Kubernetes Service for the given service entity.
     *
     * @param portVars PORT-typed variables from the service's GroovyTemplate; each one whose
     *                 {@code defaultValue} parses as an integer gets an additional Service port.
     */
    public Service build(ServiceEntity serviceEntity, String serviceName, String deploymentName,
                         List<GroovyTemplate.VariableMetadata> portVars) {
        Map<String, String> labels = Map.of(
                "app", "esb-core",
                "service-id", String.valueOf(serviceEntity.getId()),
                "version", serviceEntity.getServiceVersion()
        );

        Map<String, String> selector = Map.of(
                "app", "esb-core",
                "service-id", String.valueOf(serviceEntity.getId())
        );

        List<io.fabric8.kubernetes.api.model.ServicePort> ports = new java.util.ArrayList<>();
        ports.add(new io.fabric8.kubernetes.api.model.ServicePortBuilder()
                .withName("http")
                .withPort(KubernetesConfig.ESB_CONTAINER_PORT)
                .withTargetPort(new IntOrString(KubernetesConfig.ESB_CONTAINER_PORT))
                .withProtocol("TCP")
                .build());

        // Add a Service port for each PORT variable that has a parseable defaultValue
        if (portVars != null) {
            for (GroovyTemplate.VariableMetadata portVar : portVars) {
                Integer portNum = parsePort(portVar.getDefaultValue());
                if (portNum != null) {
                    ports.add(new io.fabric8.kubernetes.api.model.ServicePortBuilder()
                            .withName(portVar.getName())
                            .withPort(portNum)
                            .withTargetPort(new IntOrString(portNum))
                            .withProtocol("TCP")
                            .build());
                }
            }
        }

        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(serviceName)
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withType("ClusterIP")
                    .withSelector(selector)
                    .withPorts(ports)
                .endSpec()
                .build();
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
