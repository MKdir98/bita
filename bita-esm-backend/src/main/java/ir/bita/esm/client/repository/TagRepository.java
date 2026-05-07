package ir.bita.esm.client.repository;

import ir.bita.esm.client.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Tag entity.
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * Finds all tags for a client.
     */
    List<Tag> findByClientId(Long clientId);

    /**
     * Finds a tag by client ID and key.
     */
    Optional<Tag> findByClientIdAndKey(Long clientId, String key);

    /**
     * Checks if a tag with the given key exists for a client.
     */
    boolean existsByClientIdAndKey(Long clientId, String key);

    /**
     * Deletes all tags for a client.
     */
    @Modifying
    @Query("DELETE FROM Tag t WHERE t.client.id = :clientId")
    void deleteByClientId(@Param("clientId") Long clientId);

    /**
     * Finds clients by tag key and value.
     */
    @Query("SELECT t.client.id FROM Tag t WHERE t.key = :key AND t.value = :value")
    List<Long> findClientIdsByKeyAndValue(@Param("key") String key, @Param("value") String value);
}
