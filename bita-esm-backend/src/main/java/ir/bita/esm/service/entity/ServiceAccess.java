package ir.bita.esm.service.entity;

import ir.bita.common.domain.AccessStatus;
import ir.bita.esm.client.entity.Client;
import ir.bita.esm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * Service Access entity for managing client access to services.
 */
@Entity
@Table(name = "service_access", indexes = {
        @Index(name = "idx_access_client", columnList = "client_id"),
        @Index(name = "idx_access_service", columnList = "service_id"),
        @Index(name = "idx_access_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_client_service_access", columnNames = {"client_id", "service_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class ServiceAccess extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccessStatus status = AccessStatus.ACTIVE;

    /**
     * Custom rate limit for this client on this service (requests per minute).
     * Null means use the endpoint's default rate limit.
     */
    @Column(name = "custom_rate_limit")
    private Integer customRateLimit;

    /**
     * When this access becomes valid.
     */
    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    /**
     * When this access expires. Null means no expiration.
     */
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    /**
     * Reason for granting access.
     */
    @Column(name = "grant_reason", length = 500)
    private String grantReason;

    /**
     * Reason for revocation (if revoked).
     */
    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;

    /**
     * Checks if access is currently valid.
     */
    public boolean isCurrentlyValid() {
        if (status != AccessStatus.ACTIVE) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        if (validUntil != null && now.isAfter(validUntil)) {
            return false;
        }
        return true;
    }

    /**
     * Checks if access has expired.
     */
    public boolean isExpired() {
        return validUntil != null && LocalDateTime.now().isAfter(validUntil);
    }

    /**
     * Revokes this access.
     */
    public void revoke(String reason) {
        this.status = AccessStatus.REVOKED;
        this.revokeReason = reason;
    }

    /**
     * Marks this access as expired.
     */
    public void markExpired() {
        this.status = AccessStatus.EXPIRED;
    }

    /**
     * Reactivates revoked or expired access.
     */
    public void reactivate() {
        this.status = AccessStatus.ACTIVE;
        this.revokeReason = null;
    }
}
