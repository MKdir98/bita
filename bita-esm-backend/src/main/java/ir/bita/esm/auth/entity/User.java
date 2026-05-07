package ir.bita.esm.auth.entity;

import ir.bita.esm.shared.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.HashSet;
import java.util.Set;

/**
 * User entity for authentication and authorization.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_mobile", columnList = "mobile_number"),
        @Index(name = "idx_users_active", columnList = "is_active, is_deleted")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class User extends SoftDeletableEntity {

    @Column(name = "mobile_number", unique = true, nullable = false, length = 11)
    private String mobileNumber;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @ElementCollection(targetClass = Role.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /**
     * Adds a role to the user.
     */
    public void addRole(Role role) {
        this.roles.add(role);
    }

    /**
     * Removes a role from the user.
     */
    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    /**
     * Checks if user has a specific role.
     */
    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    /**
     * Checks if user is an admin.
     */
    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
