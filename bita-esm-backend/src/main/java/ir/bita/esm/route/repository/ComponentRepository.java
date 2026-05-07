package ir.bita.esm.route.repository;

import ir.bita.esm.route.entity.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Component entity.
 */
@Repository
public interface ComponentRepository extends JpaRepository<Component, Long> {

    List<Component> findByRouteIdOrderByOrderIndexAsc(Long routeId);

    List<Component> findByTemplateId(Long templateId);

    Page<Component> findAll(Pageable pageable);

    void deleteByRouteId(Long routeId);
}
