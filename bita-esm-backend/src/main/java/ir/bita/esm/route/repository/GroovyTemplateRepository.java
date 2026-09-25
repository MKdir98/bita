package ir.bita.esm.route.repository;

import ir.bita.esm.route.entity.GroovyTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroovyTemplateRepository extends JpaRepository<GroovyTemplate, Long> {

    Optional<GroovyTemplate> findByIdAndDeletedFalse(Long id);

    Optional<GroovyTemplate> findByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalse(String name);

    Page<GroovyTemplate> findByDeletedFalse(Pageable pageable);

    long countByDeletedFalse();
}
