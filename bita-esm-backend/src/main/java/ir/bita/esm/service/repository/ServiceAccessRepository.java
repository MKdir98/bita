package ir.bita.esm.service.repository;

import ir.bita.common.domain.AccessStatus;
import ir.bita.esm.service.entity.ServiceAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ServiceAccess entity.
 */
@Repository
public interface ServiceAccessRepository extends JpaRepository<ServiceAccess, Long> {

    /**
     * Finds access by client and service.
     */
    Optional<ServiceAccess> findByClientIdAndServiceId(Long clientId, Long serviceId);

    /**
     * Finds all access rules for a client.
     */
    List<ServiceAccess> findByClientId(Long clientId);

    /**
     * Finds all access rules for a service.
     */
    List<ServiceAccess> findByServiceId(Long serviceId);

    /**
     * Finds active access for a client and service.
     */
    @Query("SELECT a FROM ServiceAccess a WHERE a.client.id = :clientId AND a.service.id = :serviceId " +
           "AND a.status = 'ACTIVE' " +
           "AND (a.validFrom IS NULL OR a.validFrom <= :now) " +
           "AND (a.validUntil IS NULL OR a.validUntil > :now)")
    Optional<ServiceAccess> findValidAccess(
            @Param("clientId") Long clientId,
            @Param("serviceId") Long serviceId,
            @Param("now") LocalDateTime now
    );

    /**
     * Checks if client has valid access to service.
     */
    @Query("SELECT COUNT(a) > 0 FROM ServiceAccess a WHERE a.client.id = :clientId AND a.service.id = :serviceId " +
           "AND a.status = 'ACTIVE' " +
           "AND (a.validFrom IS NULL OR a.validFrom <= :now) " +
           "AND (a.validUntil IS NULL OR a.validUntil > :now)")
    boolean hasValidAccess(
            @Param("clientId") Long clientId,
            @Param("serviceId") Long serviceId,
            @Param("now") LocalDateTime now
    );

    /**
     * Finds all access rules with pagination.
     */
    Page<ServiceAccess> findAll(Pageable pageable);

    /**
     * Finds access rules by status.
     */
    Page<ServiceAccess> findByStatus(AccessStatus status, Pageable pageable);

    /**
     * Finds expired access rules that are still marked as ACTIVE.
     */
    @Query("SELECT a FROM ServiceAccess a WHERE a.status = 'ACTIVE' " +
           "AND a.validUntil IS NOT NULL AND a.validUntil < :now")
    List<ServiceAccess> findExpiredActiveAccess(@Param("now") LocalDateTime now);

    /**
     * Updates expired access to EXPIRED status.
     */
    @Modifying
    @Query("UPDATE ServiceAccess a SET a.status = 'EXPIRED' WHERE a.status = 'ACTIVE' " +
           "AND a.validUntil IS NOT NULL AND a.validUntil < :now")
    int markExpiredAccess(@Param("now") LocalDateTime now);

    /**
     * Counts access by status.
     */
    long countByStatus(AccessStatus status);
}
