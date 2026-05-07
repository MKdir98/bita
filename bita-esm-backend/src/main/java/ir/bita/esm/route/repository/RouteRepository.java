package ir.bita.esm.route.repository;

import ir.bita.esm.route.entity.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Route entity.
 */
@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByIdAndDeletedFalse(Long id);

    Optional<Route> findByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalse(String name);

    Page<Route> findByDeletedFalse(Pageable pageable);

    List<Route> findByServiceIdAndDeletedFalse(Long serviceId);

    Page<Route> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

    @Query("SELECT r FROM Route r WHERE r.service.id = :serviceId AND r.active = true AND r.deleted = false")
    List<Route> findActiveRoutesByService(@Param("serviceId") Long serviceId);

    @Query("SELECT r FROM Route r JOIN FETCH r.fromEndpoint JOIN FETCH r.toEndpoint WHERE r.deleted = false")
    List<Route> findAllWithEndpoints();

    long countByDeletedFalse();

    long countByServiceIdAndDeletedFalse(Long serviceId);
}
