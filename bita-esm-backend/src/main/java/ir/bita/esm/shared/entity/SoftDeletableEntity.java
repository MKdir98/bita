package ir.bita.esm.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

/**
 * Base entity with soft delete capability.
 */
@Getter
@Setter
@MappedSuperclass
@Audited
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    /**
     * Soft delete this entity.
     */
    public void softDelete() {
        this.deleted = true;
        this.active = false;
    }

    /**
     * Restore a soft-deleted entity.
     */
    public void restore() {
        this.deleted = false;
        this.active = true;
    }
}
