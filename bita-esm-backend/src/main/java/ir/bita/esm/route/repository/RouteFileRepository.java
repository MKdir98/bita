package ir.bita.esm.route.repository;

import ir.bita.common.domain.FileType;
import ir.bita.esm.route.entity.RouteFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RouteFile entity.
 */
@Repository
public interface RouteFileRepository extends JpaRepository<RouteFile, Long> {

    List<RouteFile> findByRouteId(Long routeId);

    Optional<RouteFile> findByIdAndRouteId(Long id, Long routeId);

    List<RouteFile> findByRouteIdAndFileType(Long routeId, FileType fileType);

    boolean existsByRouteIdAndFilename(Long routeId, String filename);

    void deleteByRouteId(Long routeId);

    long countByRouteId(Long routeId);
}
