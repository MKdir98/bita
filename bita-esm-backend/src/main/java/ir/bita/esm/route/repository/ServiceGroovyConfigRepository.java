package ir.bita.esm.route.repository;

import ir.bita.esm.route.entity.ServiceGroovyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceGroovyConfigRepository extends JpaRepository<ServiceGroovyConfig, Long> {

    Optional<ServiceGroovyConfig> findByServiceId(Long serviceId);

    boolean existsByServiceId(Long serviceId);
}
