package ir.bita.common.domain;

/**
 * Enum representing the lifecycle phases of a service.
 * Services progress through these phases from creation to production.
 */
public enum ServicePhase {

    /**
     * Service is being developed.
     * Not deployed to Kubernetes.
     * Only visible to the creator.
     */
    DRAFT("Draft", "Service is under development", false),

    /**
     * Service is ready for testing.
     * Deployed to Kubernetes test environment.
     * Visible to test organization and selected clients.
     */
    TEST("Test", "Service is in testing phase", true),

    /**
     * Service is active and deployed to production.
     * Kubernetes resources are created (Deployment, Service, HPA, Ingress).
     * Visible to all authorized clients.
     */
    ACTIVE("Active", "Service is live in production", true);

    private final String displayName;
    private final String description;
    private final boolean deployed;

    ServicePhase(String displayName, String description, boolean deployed) {
        this.displayName = displayName;
        this.description = description;
        this.deployed = deployed;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns true if services in this phase are deployed to Kubernetes.
     */
    public boolean isDeployed() {
        return deployed;
    }

    /**
     * Checks if transition to the target phase is allowed.
     *
     * @param target the target phase
     * @return true if transition is allowed
     */
    public boolean canTransitionTo(ServicePhase target) {
        if (this == target) {
            return false; // No self-transition
        }
        
        return switch (this) {
            case DRAFT -> target == TEST || target == ACTIVE;
            case TEST -> target == DRAFT || target == ACTIVE;
            case ACTIVE -> target == TEST || target == DRAFT;
        };
    }
}
