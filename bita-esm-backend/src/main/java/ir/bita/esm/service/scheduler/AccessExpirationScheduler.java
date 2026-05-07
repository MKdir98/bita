package ir.bita.esm.service.scheduler;

import ir.bita.common.event.AccessExpiredEvent;
import ir.bita.esm.service.entity.ServiceAccess;
import ir.bita.esm.service.repository.ServiceAccessRepository;
import ir.bita.esm.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled task for expiring service access.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessExpirationScheduler {

    private final ServiceAccessRepository accessRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Runs every 5 minutes to check for expired access.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void checkAndExpireAccess() {
        log.debug("Running access expiration check...");

        LocalDateTime now = LocalDateTime.now();
        
        // Find all access that should be expired
        List<ServiceAccess> expiredAccess = accessRepository.findExpiredActiveAccess(now);
        
        if (expiredAccess.isEmpty()) {
            log.debug("No expired access found");
            return;
        }

        log.info("Found {} access rules to expire", expiredAccess.size());

        for (ServiceAccess access : expiredAccess) {
            access.markExpired();
            accessRepository.save(access);

            // Publish event
            AccessExpiredEvent event = AccessExpiredEvent.builder()
                    .accessId(access.getId())
                    .clientId(access.getClient().getId())
                    .serviceId(access.getService().getId())
                    .build();
            eventPublisher.publish(event);

            log.info("Expired access {} for client {} to service {}",
                    access.getId(), access.getClient().getId(), access.getService().getId());
        }
    }

    /**
     * Alternative batch update method (more efficient for large datasets).
     */
    @Transactional
    public int expireAccessBatch() {
        int updated = accessRepository.markExpiredAccess(LocalDateTime.now());
        if (updated > 0) {
            log.info("Batch expired {} access rules", updated);
        }
        return updated;
    }
}
