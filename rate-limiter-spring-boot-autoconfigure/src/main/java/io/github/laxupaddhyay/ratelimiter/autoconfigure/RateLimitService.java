package io.github.laxupaddhyay.ratelimiter.autoconfigure;

/** Checks and atomically consumes the applicable user and API-key buckets. */
@FunctionalInterface
public interface RateLimitService {
    RateLimitDecision check(RateLimitIdentity identity, RateLimitPolicies policies);
}
