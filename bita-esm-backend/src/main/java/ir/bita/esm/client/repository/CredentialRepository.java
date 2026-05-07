package ir.bita.esm.client.repository;

import ir.bita.common.domain.CredentialType;
import ir.bita.esm.client.entity.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Credential entity.
 */
@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {

    /**
     * Finds all credentials for a client.
     */
    List<Credential> findByClientId(Long clientId);

    /**
     * Finds active credentials for a client.
     */
    List<Credential> findByClientIdAndActiveTrue(Long clientId);

    /**
     * Finds a credential by client ID and credential ID.
     */
    Optional<Credential> findByIdAndClientId(Long id, Long clientId);

    /**
     * Finds credentials by type and value.
     */
    List<Credential> findByCredentialTypeAndCredentialValue(CredentialType type, String value);

    /**
     * Finds active credentials by type and value.
     */
    @Query("SELECT c FROM Credential c WHERE c.credentialType = :type AND c.credentialValue = :value " +
           "AND c.active = true AND (c.expiresAt IS NULL OR c.expiresAt > :now)")
    List<Credential> findValidCredentials(
            @Param("type") CredentialType type,
            @Param("value") String value,
            @Param("now") LocalDateTime now
    );

    /**
     * Finds credentials by IP address (for IP-based authentication).
     */
    @Query("SELECT c FROM Credential c WHERE c.credentialType = 'IP_ADDRESS' " +
           "AND c.credentialValue = :ip AND c.active = true " +
           "AND (c.expiresAt IS NULL OR c.expiresAt > :now)")
    List<Credential> findByIpAddress(@Param("ip") String ip, @Param("now") LocalDateTime now);

    /**
     * Checks if a credential value already exists for a type.
     */
    boolean existsByCredentialTypeAndCredentialValue(CredentialType type, String value);

    /**
     * Finds expired credentials that are still active.
     */
    @Query("SELECT c FROM Credential c WHERE c.active = true AND c.expiresAt IS NOT NULL AND c.expiresAt < :now")
    List<Credential> findExpiredActiveCredentials(@Param("now") LocalDateTime now);

    /**
     * Counts credentials for a client.
     */
    long countByClientId(Long clientId);
}
