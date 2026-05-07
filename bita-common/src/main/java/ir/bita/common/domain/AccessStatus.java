package ir.bita.common.domain;

/**
 * Enum representing the status of a client's access to a service.
 */
public enum AccessStatus {

    /**
     * Access is currently active and valid.
     * Client can call the service.
     */
    ACTIVE("Active", "Access is active and valid"),

    /**
     * Access has been revoked by an administrator.
     * Client cannot call the service.
     */
    REVOKED("Revoked", "Access has been revoked"),

    /**
     * Access has expired based on the valid_until date.
     * Client cannot call the service.
     */
    EXPIRED("Expired", "Access has expired");

    private final String displayName;
    private final String description;

    AccessStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if this status allows access to the service.
     */
    public boolean allowsAccess() {
        return this == ACTIVE;
    }

    /**
     * Checks if this status can be reactivated.
     */
    public boolean canReactivate() {
        return this == REVOKED || this == EXPIRED;
    }
}
