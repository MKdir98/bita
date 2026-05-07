package ir.bita.esm.service.repository;

import ir.bita.esm.service.entity.ServiceCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for ServiceCollection entity.
 */
@Repository
public interface ServiceCollectionRepository extends JpaRepository<ServiceCollection, Long> {

    /**
     * Finds an active collection by ID.
     */
    Optional<ServiceCollection> findByIdAndDeletedFalse(Long id);

    /**
     * Finds a collection by name.
     */
    Optional<ServiceCollection> findByNameAndDeletedFalse(String name);

    /**
     * Finds a collection by base path.
     */
    Optional<ServiceCollection> findByBasePathAndDeletedFalse(String basePath);

    /**
     * Checks if a collection exists with the given name.
     */
    boolean existsByNameAndDeletedFalse(String name);

    /**
     * Checks if a collection exists with the given base path.
     */
    boolean existsByBasePathAndDeletedFalse(String basePath);

    /**
     * Finds all active collections.
     */
    Page<ServiceCollection> findByDeletedFalse(Pageable pageable);

    /**
     * Search collections by name.
     */
    Page<ServiceCollection> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

    /**
     * Counts active collections.
     */
    long countByDeletedFalse();
}
