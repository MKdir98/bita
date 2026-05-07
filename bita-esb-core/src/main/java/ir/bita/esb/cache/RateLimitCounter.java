package ir.bita.esb.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding window rate limit counter.
 */
@Slf4j
public class RateLimitCounter {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Data
    @AllArgsConstructor
    private static class WindowCounter {
        private long windowStart;
        private AtomicInteger count;
    }

    /**
     * Increment counter and check if within limit.
     * Uses sliding window algorithm.
     *
     * @param key Unique key (e.g., clientId:routeId)
     * @param limit Maximum requests allowed
     * @param windowMs Window size in milliseconds
     * @return true if within limit, false if exceeded
     */
    public boolean incrementAndCheck(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();

        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || (now - existing.getWindowStart()) >= windowMs) {
                // Start new window
                return new WindowCounter(now, new AtomicInteger(1));
            }

            // Within same window, increment
            existing.getCount().incrementAndGet();
            return existing;
        });

        int currentCount = counter.getCount().get();
        boolean allowed = currentCount <= limit;

        if (!allowed) {
            log.debug("Rate limit exceeded for key {}: {} > {}", key, currentCount, limit);
        }

        return allowed;
    }

    /**
     * Get current count for a key.
     */
    public int getCount(String key) {
        WindowCounter counter = counters.get(key);
        return counter != null ? counter.getCount().get() : 0;
    }

    /**
     * Reset counter for a key.
     */
    public void reset(String key) {
        counters.remove(key);
    }

    /**
     * Clear all counters.
     */
    public void clear() {
        counters.clear();
    }

    /**
     * Clean up expired windows.
     * Call this periodically to prevent memory leaks.
     */
    public void cleanup(long maxAge) {
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(entry -> 
            (now - entry.getValue().getWindowStart()) >= maxAge);
    }
}
