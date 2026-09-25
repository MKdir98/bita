package ir.bita.esm.route.repository;

import ir.bita.esm.route.entity.ServiceConfigVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceConfigVersionRepository extends JpaRepository<ServiceConfigVersion, Long> {

    List<ServiceConfigVersion> findByServiceIdOrderByMinorAsc(Long serviceId);

    Optional<ServiceConfigVersion> findByServiceIdAndMinor(Long serviceId, int minor);

    Optional<ServiceConfigVersion> findByServiceIdAndActiveTrue(Long serviceId);

    @Query("select coalesce(max(v.minor), 0) from ServiceConfigVersion v where v.service.id = :serviceId")
    int maxMinor(@Param("serviceId") Long serviceId);
}
