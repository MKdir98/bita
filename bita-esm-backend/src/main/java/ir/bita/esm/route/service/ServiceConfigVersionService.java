package ir.bita.esm.route.service;

import ir.bita.esm.route.entity.GroovyTemplate;
import ir.bita.esm.route.entity.ServiceConfigVersion;
import ir.bita.esm.route.entity.ServiceGroovyConfig;
import ir.bita.esm.route.repository.ServiceConfigVersionRepository;
import ir.bita.esm.route.repository.ServiceGroovyConfigRepository;
import ir.bita.esm.service.entity.ServiceEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minor versions of a service's configuration. Every approved change becomes a new immutable
 * version and is made active; making an older version active again is the rollback. The active
 * version is copied into {@link ServiceGroovyConfig} (what the ESB syncs) and the running ESB is
 * told to reload once the change commits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceConfigVersionService {

    private final ServiceConfigVersionRepository versionRepository;
    private final ServiceGroovyConfigRepository configRepository;
    private final EsbReloadNotifier reloadNotifier;

    @Transactional
    public ServiceConfigVersion recordAndActivate(ServiceEntity service, GroovyTemplate template,
                                                  Map<String, Object> variableValues, String assembledScript,
                                                  String note) {
        ServiceConfigVersion version = versionRepository.save(ServiceConfigVersion.builder()
                .service(service)
                .minor(versionRepository.maxMinor(service.getId()) + 1)
                .groovyTemplate(template)
                .variableValues(new HashMap<>(variableValues))
                .assembledScript(assembledScript)
                .note(note)
                .active(false)
                .build());
        activate(service, version);
        log.info("Service {} config version {} created and activated", service.getId(), version.label());
        return version;
    }

    /** Makes an existing version active again. */
    @Transactional
    public ServiceConfigVersion rollback(ServiceEntity service, int minor) {
        ServiceConfigVersion target = versionRepository.findByServiceIdAndMinor(service.getId(), minor)
                .orElseThrow(() -> new IllegalArgumentException("نسخهٔ " + minor + " برای این سرویس وجود ندارد"));
        activate(service, target);
        log.info("Service {} rolled back to config version {}", service.getId(), target.label());
        return target;
    }

    @Transactional(readOnly = true)
    public List<ServiceConfigVersion> versions(Long serviceId) {
        return versionRepository.findByServiceIdOrderByMinorAsc(serviceId);
    }

    @Transactional(readOnly = true)
    public Optional<ServiceConfigVersion> active(Long serviceId) {
        return versionRepository.findByServiceIdAndActiveTrue(serviceId);
    }

    /** Label of the active version (e.g. "1.6"), resolved inside a transaction. */
    @Transactional(readOnly = true)
    public Optional<String> activeLabel(Long serviceId) {
        return versionRepository.findByServiceIdAndActiveTrue(serviceId).map(ServiceConfigVersion::label);
    }

    private void activate(ServiceEntity service, ServiceConfigVersion target) {
        versionRepository.findByServiceIdAndActiveTrue(service.getId())
                .filter(current -> !current.getId().equals(target.getId()))
                .ifPresent(current -> {
                    current.setActive(false);
                    versionRepository.saveAndFlush(current);
                });
        target.setActive(true);
        versionRepository.save(target);

        ServiceGroovyConfig config = configRepository.findByServiceId(service.getId())
                .orElseGet(() -> ServiceGroovyConfig.builder().service(service).build());
        config.setGroovyTemplate(target.getGroovyTemplate());
        config.setVariableValues(new HashMap<>(target.getVariableValues()));
        config.setAssembledScript(target.getAssembledScript());
        configRepository.save(config);

        reloadNotifier.reloadAfterCommit(service.getId());
    }
}
