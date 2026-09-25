package ir.bita.esm.route.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

/**
 * Tells a service's running ESB to reload its config — after the change is committed, so the
 * ESB never fetches the old one. An ESB that is not running (or not reachable) simply picks the
 * active version up when it next starts; that is not an error.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EsbReloadNotifier {

    private final EsbInstanceLocator locator;
    private final RestTemplate restTemplate;

    /** The service's configuration changed: the ESB swaps in the active version. */
    public void reloadAfterCommit(Long serviceId) {
        afterCommit(serviceId, "/internal/reload");
    }

    /** Who may call the service changed (access granted/revoked): the ESB re-syncs clients, access and certificates. */
    public void resyncAfterCommit(Long serviceId) {
        afterCommit(serviceId, "/internal/resync");
    }

    private void afterCommit(Long serviceId, String path) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    call(serviceId, path);
                }
            });
        } else {
            call(serviceId, path);
        }
    }

    private void call(Long serviceId, String path) {
        locator.baseUrl(serviceId).ifPresentOrElse(base -> {
            try {
                restTemplate.postForEntity(base + path, null, String.class);
                log.info("ESB of service {} notified: {} ({})", serviceId, path, base);
            } catch (Exception e) {
                log.warn("ESB of service {} not notified of {} ({}): {}", serviceId, path, base, e.getMessage());
            }
        }, () -> log.debug("Service {} has no running ESB; it will pick the change up on start", serviceId));
    }
}
