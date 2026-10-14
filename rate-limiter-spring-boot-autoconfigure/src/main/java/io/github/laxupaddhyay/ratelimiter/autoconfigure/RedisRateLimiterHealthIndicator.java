package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;

/** Reports Redis connectivity for actuator health and readiness endpoints. */
public final class RedisRateLimiterHealthIndicator implements HealthIndicator {
    private final StringRedisTemplate redis;

    public RedisRateLimiterHealthIndicator(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public Health health() {
        try {
            String response = redis.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(response)
                    ? Health.up().withDetail("store", "redis").build()
                    : Health.down().withDetail("store", "redis").build();
        } catch (DataAccessException exception) {
            return Health.down().withDetail("store", "redis").build();
        }
    }
}
