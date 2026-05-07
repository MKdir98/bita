package ir.bita.esm.auth.entity;

/**
 * User roles in the system.
 */
public enum Role {
    /**
     * Full administrative access.
     */
    ADMIN,
    
    /**
     * Can manage services and routes.
     */
    SERVICE_MANAGER,
    
    /**
     * Can manage clients and credentials.
     */
    CLIENT_MANAGER,
    
    /**
     * Can manage service access permissions.
     */
    ACCESS_MANAGER,
    
    /**
     * Read-only access.
     */
    VIEWER
}
