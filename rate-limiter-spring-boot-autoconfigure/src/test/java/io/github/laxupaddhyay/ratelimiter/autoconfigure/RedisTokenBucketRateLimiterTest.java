package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.actuate.health.Status;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisTokenBucketRateLimiterTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void enforcesBurstCapacityAndRefillsOverTime() throws Exception {
        RateLimitService limiter = limiter(1, 1, Duration.ofSeconds(1), 10, 10, Duration.ofMinutes(1));
        RateLimitIdentity user = user("burst");

        RateLimitPolicies policies = policies(1, 1, Duration.ofSeconds(1), 10, 10, Duration.ofMinutes(1));
        assertThat(limiter.check(user, policies).allowed()).isTrue();
        RateLimitDecision denied = limiter.check(user, policies);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(1);
        Thread.sleep(1_100);
        assertThat(limiter.check(user, policies).allowed()).isTrue();
    }

    @Test
    void concurrentRequestsNeverExceedCapacity() throws Exception {
        RateLimitService limiter = limiter(10, 1, Duration.ofMinutes(1), 10, 1, Duration.ofMinutes(1));
        var pool = Executors.newFixedThreadPool(12);
        try {
            AtomicInteger allowed = new AtomicInteger();
            var tasks = java.util.stream.IntStream.range(0, 40).<java.util.concurrent.Callable<Void>>mapToObj(i -> () -> {
                if (limiter.check(user("concurrent"), policies(10, 1, Duration.ofMinutes(1),
                        10, 1, Duration.ofMinutes(1))).allowed()) allowed.incrementAndGet();
                return null;
            }).toList();
            for (var future : pool.invokeAll(tasks)) future.get();
            assertThat(allowed.get()).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void deniedDualIdentityRequestDoesNotConsumeTheOtherBucket() {
        RateLimitService limiter = limiter(1, 1, Duration.ofMinutes(1), 1, 1, Duration.ofMinutes(1));
        RateLimitPolicies policies = policies(1, 1, Duration.ofMinutes(1), 1, 1, Duration.ofMinutes(1));
        assertThat(limiter.check(user("atomic"), policies).allowed()).isTrue();

        RateLimitIdentity both = new RateLimitIdentity(Optional.of("atomic"), Optional.of("client-a"));
        assertThat(limiter.check(both, policies).allowed()).isFalse();
        assertThat(limiter.check(apiKey("client-a"), policies).allowed()).isTrue();
    }

    @Test
    void bucketExpiresAfterItCouldHaveFullyRefilled() throws Exception {
        RateLimitService limiter = limiter(1, 1, Duration.ofSeconds(1), 10, 10, Duration.ofMinutes(1));
        RateLimitPolicies policies = policies(1, 1, Duration.ofSeconds(1), 10, 10, Duration.ofMinutes(1));
        java.util.Set<String> before = redis.keys("rate-limit:user:*");
        assertThat(limiter.check(user("expiry"), policies).allowed()).isTrue();
        java.util.Set<String> after = redis.keys("rate-limit:user:*");
        after.removeAll(before);
        assertThat(after).hasSize(1);
        String bucketKey = after.iterator().next();
        Thread.sleep(2_200);
        assertThat(redis.hasKey(bucketKey)).isFalse();
    }

    @Test
    void manyDistinctIdentitiesReceiveIsolatedExpiringBuckets() {
        RateLimitService limiter = limiter(2, 1, Duration.ofMinutes(1), 2, 1, Duration.ofMinutes(1));
        RateLimitPolicies policies = policies(2, 1, Duration.ofMinutes(1), 2, 1, Duration.ofMinutes(1));
        int before = redis.keys("rate-limit:user:*").size();

        for (int i = 0; i < 250; i++) {
            assertThat(limiter.check(user("high-cardinality-" + i), policies).allowed()).isTrue();
        }

        assertThat(redis.keys("rate-limit:user:*").size()).isGreaterThanOrEqualTo(before + 250);
        assertThat(redis.getExpire(redis.keys("rate-limit:user:*").stream().findFirst().orElseThrow()))
                .isPositive();
    }

    @Test
    void reconnectsAfterRedisContainerRestart() {
        RateLimitPolicies policies = policies(2, 1, Duration.ofMinutes(1), 2, 1, Duration.ofMinutes(1));
        assertThat(new RedisRateLimiterHealthIndicator(redis).health().getStatus()).isEqualTo(Status.UP);
        if (connectionFactory != null) connectionFactory.destroy();
        REDIS.stop();
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        RateLimitService limiter = limiter(2, 1, Duration.ofMinutes(1), 2, 1, Duration.ofMinutes(1));
        assertThat(limiter.check(user("after-redis-restart"), policies).allowed()).isTrue();
        assertThat(new RedisRateLimiterHealthIndicator(redis).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void recordsRedisCommandLatencyAndOutcomes() {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        RateLimiterProperties properties = new RateLimiterProperties();
        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(redis, properties, metrics);
        RateLimitPolicies policies = policies(2, 1, Duration.ofMinutes(1), 2, 1, Duration.ofMinutes(1));

        limiter.check(user("metric-user"), policies);

        assertThat(metrics.counter("rate_limiter.redis.commands", "outcome", "allowed").count()).isEqualTo(1);
        assertThat(metrics.timer("rate_limiter.redis.command.duration", "operation", "token_bucket").count())
                .isEqualTo(1);
    }

    private RateLimitService limiter(long userCapacity, long userRefill, Duration userPeriod,
                                     long keyCapacity, long keyRefill, Duration keyPeriod) {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setRedisKeyPrefix("rate-limit");
        properties.setUser(new RateLimiterProperties.Policy(userCapacity, userRefill, userPeriod));
        properties.setApiKey(new RateLimiterProperties.Policy(keyCapacity, keyRefill, keyPeriod));
        return new RedisTokenBucketRateLimiter(redis, properties, new SimpleMeterRegistry());
    }

    private RateLimitPolicies policies(long userCapacity, long userRefill, Duration userPeriod,
                                       long keyCapacity, long keyRefill, Duration keyPeriod) {
        return new RateLimitPolicies(new RateLimiterProperties.Policy(userCapacity, userRefill, userPeriod),
                new RateLimiterProperties.Policy(keyCapacity, keyRefill, keyPeriod));
    }

    private RateLimitIdentity user(String name) {
        return new RateLimitIdentity(Optional.of(name), Optional.empty());
    }

    private RateLimitIdentity apiKey(String name) {
        return new RateLimitIdentity(Optional.empty(), Optional.of(name));
    }
}
