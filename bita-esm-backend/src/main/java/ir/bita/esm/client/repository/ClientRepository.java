package ir.bita.esm.client.repository;

import ir.bita.esm.client.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Client entity.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Finds an active client by ID.
     */
    Optional<Client> findByIdAndDeletedFalse(Long id);

    /**
     * Finds a client by name.
     */
    Optional<Client> findByName(String name);

    /**
     * Finds an active client by name.
     */
    Optional<Client> findByNameAndDeletedFalse(String name);

    /**
     * Checks if a client exists with the given name.
     */
    boolean existsByName(String name);

    /**
     * Checks if an active client exists with the given name.
     */
    boolean existsByNameAndDeletedFalse(String name);

    /**
     * Finds all active clients (not deleted).
     */
    Page<Client> findByDeletedFalse(Pageable pageable);

    /**
     * Finds active clients by tag key.
     */
    @Query("SELECT DISTINCT c FROM Client c JOIN c.tags t WHERE t.key = :key AND c.deleted = false")
    Page<Client> findByTagKey(@Param("key") String key, Pageable pageable);

    /**
     * Finds active clients by tag key and value.
     */
    @Query("SELECT DISTINCT c FROM Client c JOIN c.tags t WHERE t.key = :key AND t.value = :value AND c.deleted = false")
    Page<Client> findByTagKeyAndValue(@Param("key") String key, @Param("value") String value, Pageable pageable);

    /**
     * Search clients by name containing.
     */
    Page<Client> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

    /**
     * Finds all child clients of a parent.
     */
    List<Client> findByParentIdAndDeletedFalse(Long parentId);

    /**
     * Counts active clients.
     */
    long countByDeletedFalse();
}
