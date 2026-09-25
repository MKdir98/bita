package ir.bita.esm.k8s;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.service.entity.ServiceEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builder for Kubernetes Deployment resources.
 */
@Component
public class K8sDeploymentBuilder {

    @Value("${esm.internal.base-url:http://bita-esm-backend:8081}")
    private String esmBaseUrl;

    @Value("${esm.internal.api-key:}")
    private String esmApiKey;

    /** Filesystem path inside the ESB pod where the BITA keystore properties file is mounted. */
    @Value("${esb.bita-keystore-path:/etc/esb/secrets/bita-keystore.properties}")
    private String bitaKeystorePath;

    /** BITA keystore password – override via K8s Secret in production. */
    @Value("${esb.bita-keystore-password:changeit}")
    private String bitaKeystorePassword;

    /**
     * Builds a Kubernetes Deployment for the given service.
     *
     * @param portVars PORT-typed variables from the service's GroovyTemplate; each one whose
     *                 {@code defaultValue} parses as an integer gets an additional containerPort.
     */
    public Deployment build(ServiceEntity service, String deploymentName, List<GroovyTemplate.VariableMetadata> portVars) {
        Map<String, String> labels = Map.of(
                "app", "esb-core",
                "service-id", String.valueOf(service.getId()),
                "version", service.getServiceVersion()
        );

        ContainerBuilder containerBuilder = new ContainerBuilder()
                .withName("esb-core")
                .withImage(KubernetesConfig.ESB_CORE_IMAGE)
                .withImagePullPolicy("Always")
                .addNewPort()
                    .withContainerPort(KubernetesConfig.ESB_CONTAINER_PORT)
                    .withProtocol("TCP")
                .endPort();

        // Add a containerPort for each PORT variable that has a parseable defaultValue
        if (portVars != null) {
            for (GroovyTemplate.VariableMetadata portVar : portVars) {
                Integer portNum = parsePort(portVar.getDefaultValue());
                if (portNum != null) {
                    containerBuilder = containerBuilder
                            .addNewPort()
                                .withContainerPort(portNum)
                                .withProtocol("TCP")
                            .endPort();
                }
            }
        }

        containerBuilder = containerBuilder
                .addNewEnv()
                    .withName("SERVICE_ID")
                    .withValue(String.valueOf(service.getId()))
                .endEnv()
                .addNewEnv()
                    .withName("SERVICE_VERSION")
                    .withValue(service.getServiceVersion())
                .endEnv()
                .addNewEnv()
                    .withName("ESM_BASE_URL")
                    .withValue(esmBaseUrl)
                .endEnv()
                .addNewEnv()
                    .withName("ESM_API_KEY")
                    .withValue(esmApiKey)
                .endEnv()
                .addNewEnv()
                    .withName("HTTP_PORT")
                    .withValue(String.valueOf(KubernetesConfig.ESB_CONTAINER_PORT))
                .endEnv()
                .addNewEnv()
                    .withName("BITA_KEYSTORE_PATH")
                    .withValue(bitaKeystorePath)
                .endEnv()
                .addNewEnv()
                    .withName("BITA_KEYSTORE_PASSWORD")
                    .withValue(bitaKeystorePassword)
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
                .endLivenessProbe();

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
                            .withContainers(containerBuilder.build())
                        .endSpec()
                    .endTemplate()
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
