package ir.bita.esm.route.repository;

import ir.bita.esm.route.entity.Endpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Endpoint entity.
 */
@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, Long> {

    Optional<Endpoint> findByName(String name);

    List<Endpoint> findByTemplateId(Long templateId);

    Page<Endpoint> findAll(Pageable pageable);

    Page<Endpoint> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
