package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

final class RedisTokenBucketRateLimiter implements RateLimitService {
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>();
    static {
        SCRIPT.setLocation(new org.springframework.core.io.ClassPathResource(
                "io/github/laxupaddhyay/ratelimiter/autoconfigure/token_bucket.lua"));
        SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    RedisTokenBucketRateLimiter(StringRedisTemplate redis, RateLimiterProperties properties,
                                io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.redis = redis;
        this.keyPrefix = properties.getRedisKeyPrefix();
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RateLimitDecision check(RateLimitIdentity identity, RateLimitPolicies policies) {
        List<String> keys = new ArrayList<>(2);
        List<String> args = new ArrayList<>(6);
        identity.userId().ifPresent(value -> addBucket(keys, args, "user", value, policies.user()));
        identity.apiKey().ifPresent(value -> addBucket(keys, args, "api-key", value, policies.apiKey()));
        if (keys.isEmpty()) return RateLimitDecision.pass();
        long started = System.nanoTime();
        try {
            List<?> result = redis.execute(SCRIPT, keys, args.toArray());
            if (result == null || result.size() < 2) throw new IllegalStateException("Redis rate-limit script returned an invalid response");
            boolean allowed = number(result.get(0)) == 1;
            long retry = number(result.get(1));
            meterRegistry.counter("rate_limiter.redis.commands", "outcome", allowed ? "allowed" : "limited").increment();
            return allowed ? RateLimitDecision.pass() : RateLimitDecision.limited(retry);
        } catch (org.springframework.dao.DataAccessException exception) {
            meterRegistry.counter("rate_limiter.redis.commands", "outcome", "error").increment();
            throw exception;
        } finally {
            meterRegistry.timer("rate_limiter.redis.command.duration", "operation", "token_bucket").record(
                    System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private void addBucket(List<String> keys, List<String> args, String type, String identity,
                           RateLimiterProperties.Policy policy) {
        keys.add(keyPrefix + ":" + type + ":" + hash(type + ":" + identity));
        args.add(Long.toString(policy.getCapacity()));
        args.add(Long.toString(policy.getRefillTokens()));
        args.add(Long.toString(policy.getRefillPeriod().toMillis()));
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
