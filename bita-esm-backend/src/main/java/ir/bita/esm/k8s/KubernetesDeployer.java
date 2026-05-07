package ir.bita.esm.k8s;

import ir.bita.esm.service.entity.ServiceEntity;

/**
 * Interface for Kubernetes deployment operations.
 */
public interface KubernetesDeployer {

    /**
     * Deploys a service to Kubernetes.
     * Creates Deployment, Service, HPA, and Ingress rule.
     *
     * @param service the service to deploy
     */
    void deployService(ServiceEntity service);

    /**
     * Undeploys a service from Kubernetes.
     * Removes all K8s resources.
     *
     * @param service the service to undeploy
     */
    void undeployService(ServiceEntity service);

    /**
     * Updates the scaling configuration for a deployed service.
     * Updates the HPA configuration.
     *
     * @param service the service with updated scaling config
     */
    void updateScaling(ServiceEntity service);

    /**
     * Checks if a service is deployed.
     *
     * @param service the service to check
     * @return true if deployed
     */
    boolean isDeployed(ServiceEntity service);
}
