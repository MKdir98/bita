package ir.bita.esm.route.repository;

import ir.bita.esm.route.entity.RouteTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RouteTemplate entity.
 */
@Repository
public interface RouteTemplateRepository extends JpaRepository<RouteTemplate, Long> {

    Optional<RouteTemplate> findByIdAndDeletedFalse(Long id);

    Optional<RouteTemplate> findByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalse(String name);

    Page<RouteTemplate> findByDeletedFalse(Pageable pageable);

    List<RouteTemplate> findByCategoryAndDeletedFalse(String category);

    Page<RouteTemplate> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

    long countByDeletedFalse();
}
