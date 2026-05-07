package ir.bita.esm.k8s;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kubernetes client configuration.
 */
@Configuration
@Slf4j
public class KubernetesConfig {

    @Value("${kubernetes.master-url:#{null}}")
    private String masterUrl;

    @Value("${kubernetes.namespace:default}")
    private String namespace;

    @Value("${kubernetes.in-cluster:true}")
    private boolean inCluster;

    /**
     * Default namespace for K8s resources.
     */
    public static final String DEFAULT_NAMESPACE = "bita-esb";

    /**
     * Name of the shared ingress for ESB routes.
     */
    public static final String ESB_INGRESS_NAME = "esb-ingress";

    /**
     * ESB core container image.
     */
    public static final String ESB_CORE_IMAGE = "bita/esb-core:latest";

    /**
     * Default container port for ESB services.
     */
    public static final int ESB_CONTAINER_PORT = 8080;

    @Bean
    public KubernetesClient kubernetesClient() {
        Config config;

        if (inCluster) {
            // Auto-configure from service account when running in K8s
            log.info("Creating Kubernetes client with in-cluster configuration");
            config = Config.autoConfigure(null);
        } else {
            // Configure from kubeconfig or explicit settings
            log.info("Creating Kubernetes client with external configuration");
            ConfigBuilder builder = new ConfigBuilder();
            
            if (masterUrl != null && !masterUrl.isEmpty()) {
                builder.withMasterUrl(masterUrl);
            }
            
            builder.withNamespace(namespace);
            config = builder.build();
        }

        return new KubernetesClientBuilder()
                .withConfig(config)
                .build();
    }

    @Bean
    public String kubernetesNamespace() {
        return namespace != null && !namespace.isEmpty() ? namespace : DEFAULT_NAMESPACE;
    }
}
