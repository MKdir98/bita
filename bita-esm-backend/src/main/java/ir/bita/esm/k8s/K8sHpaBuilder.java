package ir.bita.esm.k8s;

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder;
import ir.bita.esm.service.entity.ServiceEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builder for Kubernetes HorizontalPodAutoscaler resources.
 */
@Component
public class K8sHpaBuilder {

    /**
     * Builds a Kubernetes HPA for the given service.
     */
    public HorizontalPodAutoscaler build(ServiceEntity service, String deploymentName) {
        Map<String, String> labels = Map.of(
                "app", "esb-core",
                "service-id", String.valueOf(service.getId())
        );

        return new HorizontalPodAutoscalerBuilder()
                .withNewMetadata()
                    .withName(deploymentName + "-hpa")
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withNewScaleTargetRef()
                        .withApiVersion("apps/v1")
                        .withKind("Deployment")
                        .withName(deploymentName)
                    .endScaleTargetRef()
                    .withMinReplicas(service.getMinReplicas())
                    .withMaxReplicas(service.getMaxReplicas())
                    .addNewMetric()
                        .withType("Resource")
                        .withNewResource()
                            .withName("cpu")
                            .withNewTarget()
                                .withType("Utilization")
                                .withAverageUtilization(service.getTargetCpuPercent())
                            .endTarget()
                        .endResource()
                    .endMetric()
                .endSpec()
                .build();
    }
}
