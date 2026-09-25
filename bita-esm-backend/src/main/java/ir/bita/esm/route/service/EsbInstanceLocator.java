package ir.bita.esm.route.service;

import java.util.Optional;

/**
 * Where the running ESB instance of a service can be reached, if it is running. In Kubernetes
 * this is the service's cluster DNS name; tests provide their own.
 */
public interface EsbInstanceLocator {

    /** Base URL of the service's ESB (e.g. {@code http://svc-name:8080}), empty if not deployed. */
    Optional<String> baseUrl(Long serviceId);
}
