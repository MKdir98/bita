package ir.bita.esm.service.repository;

import ir.bita.common.domain.ServicePhase;
import ir.bita.esm.service.entity.ServiceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ServiceEntity.
 */
@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

    /**
     * Finds an active service by ID.
     */
    Optional<ServiceEntity> findByIdAndDeletedFalse(Long id);

    /**
     * Finds services by collection ID.
     */
    List<ServiceEntity> findByCollectionIdAndDeletedFalse(Long collectionId);

    /**
     * Finds a service by collection and version.
     */
    Optional<ServiceEntity> findByCollectionIdAndServiceVersionAndDeletedFalse(Long collectionId, String serviceVersion);

    /**
     * Checks if a version exists in a collection.
     */
    boolean existsByCollectionIdAndServiceVersionAndDeletedFalse(Long collectionId, String serviceVersion);

    /**
     * Finds services by phase.
     */
    Page<ServiceEntity> findByPhaseAndDeletedFalse(ServicePhase phase, Pageable pageable);

    /**
     * Finds all active services.
     */
    Page<ServiceEntity> findByDeletedFalse(Pageable pageable);

    /**
     * Finds active services (deployed to K8s).
     */
    @Query("SELECT s FROM ServiceEntity s WHERE s.phase = 'ACTIVE' AND s.deleted = false")
    List<ServiceEntity> findActiveServices();

    /**
     * Finds services by K8s deployment name.
     */
    Optional<ServiceEntity> findByK8sDeploymentName(String deploymentName);

    /**
     * Counts services by phase.
     */
    long countByPhaseAndDeletedFalse(ServicePhase phase);

    /**
     * Search services by name.
     */
    Page<ServiceEntity> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);
}
