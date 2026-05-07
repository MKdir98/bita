package ir.bita.esm.route.repository;

import ir.bita.common.domain.ComponentType;
import ir.bita.esm.route.entity.ComponentTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ComponentTemplate entity.
 */
@Repository
public interface ComponentTemplateRepository extends JpaRepository<ComponentTemplate, Long> {

    Optional<ComponentTemplate> findByIdAndDeletedFalse(Long id);

    Optional<ComponentTemplate> findByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalse(String name);

    Page<ComponentTemplate> findByDeletedFalse(Pageable pageable);

    List<ComponentTemplate> findByComponentTypeAndDeletedFalse(ComponentType type);

    List<ComponentTemplate> findByCategoryAndDeletedFalse(String category);

    Page<ComponentTemplate> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

    long countByDeletedFalse();
}
