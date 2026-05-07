package ir.bita.esm.route.repository;

import ir.bita.esm.route.entity.EndpointTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for EndpointTemplate entity.
 */
@Repository
public interface EndpointTemplateRepository extends JpaRepository<EndpointTemplate, Long> {

    Optional<EndpointTemplate> findByIdAndDeletedFalse(Long id);

    Optional<EndpointTemplate> findByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalse(String name);

    Page<EndpointTemplate> findByDeletedFalse(Pageable pageable);

    List<EndpointTemplate> findByCategoryAndDeletedFalse(String category);

    Page<EndpointTemplate> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

    long countByDeletedFalse();
}
