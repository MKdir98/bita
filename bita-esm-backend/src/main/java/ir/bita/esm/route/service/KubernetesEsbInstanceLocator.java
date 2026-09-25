package ir.bita.esm.route.service;

import ir.bita.esm.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** A deployed service's ESB is its Kubernetes Service, on the ESB's HTTP port. */
@Component
@RequiredArgsConstructor
public class KubernetesEsbInstanceLocator implements EsbInstanceLocator {

    private final ServiceRepository serviceRepository;

    @Value("${app.esb.http-port:8080}")
    private int esbHttpPort;

    @Override
    public Optional<String> baseUrl(Long serviceId) {
        return serviceRepository.findById(serviceId)
                .map(s -> s.getK8sServiceName())
                .filter(name -> !name.isBlank())
                .map(name -> "http://" + name + ":" + esbHttpPort);
    }
}
