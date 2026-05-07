package ir.bita.esb.cache;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for access rules for this Service.
 */
@Slf4j
public class AccessCache {

    // Key: clientId:routeId, Value: AccessRule
    private final Map<String, AccessRule> accessRules = new ConcurrentHashMap<>();
    
    // Rate limit counters
    private final RateLimitCounter rateLimitCounter = new RateLimitCounter();

    @Data
    @Builder
    public static class AccessRule {
        private String clientId;
        private Long routeId;
        private Integer rateLimit;
        private String rateLimitWindow; // MINUTE, HOUR, DAY
        private Instant expiresAt;
        private boolean active;
    }

    /**
     * Add or update an access rule.
     */
    public void putAccess(AccessRule rule) {
        String key = makeKey(rule.getClientId(), rule.getRouteId());
        accessRules.put(key, rule);
        log.debug("Access rule cached: {} -> route {}", rule.getClientId(), rule.getRouteId());
    }

    /**
     * Remove an access rule.
     */
    public void removeAccess(String clientId, Long routeId) {
        String key = makeKey(clientId, routeId);
        accessRules.remove(key);
        rateLimitCounter.reset(key);
        log.debug("Access rule removed: {} -> route {}", clientId, routeId);
    }

    /**
     * Check if client has access to a route.
     */
    public boolean hasAccess(String clientId, Long routeId) {
        String key = makeKey(clientId, routeId);
        AccessRule rule = accessRules.get(key);
        
        if (rule == null) {
            return false;
        }
        
        if (!rule.isActive()) {
            return false;
        }
        
        // Check expiration
        if (rule.getExpiresAt() != null && Instant.now().isAfter(rule.getExpiresAt())) {
            log.debug("Access expired for {} -> route {}", clientId, routeId);
            return false;
        }
        
        return true;
    }

    /**
     * Check and increment rate limit.
     * @return true if within limit, false if exceeded
     */
    public boolean checkRateLimit(String clientId, Long routeId) {
        String key = makeKey(clientId, routeId);
        AccessRule rule = accessRules.get(key);
        
        if (rule == null || rule.getRateLimit() == null || rule.getRateLimit() <= 0) {
            // No rate limit configured
            return true;
        }
        
        long windowMs = getWindowMs(rule.getRateLimitWindow());
        return rateLimitCounter.incrementAndCheck(key, rule.getRateLimit(), windowMs);
    }

    /**
     * Get access rule details.
     */
    public AccessRule getAccess(String clientId, Long routeId) {
        return accessRules.get(makeKey(clientId, routeId));
    }

    /**
     * Clear all access rules.
     */
    public void clear() {
        accessRules.clear();
        rateLimitCounter.clear();
        log.info("Access cache cleared");
    }

    public int size() {
        return accessRules.size();
    }

    private String makeKey(String clientId, Long routeId) {
        return clientId + ":" + routeId;
    }

    private long getWindowMs(String window) {
        if (window == null) return 60_000L; // Default: 1 minute
        
        return switch (window.toUpperCase()) {
            case "SECOND" -> 1_000L;
            case "MINUTE" -> 60_000L;
            case "HOUR" -> 3_600_000L;
            case "DAY" -> 86_400_000L;
            default -> 60_000L;
        };
    }
}
