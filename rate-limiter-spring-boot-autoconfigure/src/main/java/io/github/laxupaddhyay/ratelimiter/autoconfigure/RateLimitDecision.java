package io.github.laxupaddhyay.ratelimiter.autoconfigure;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    public static RateLimitDecision pass() { return new RateLimitDecision(true, 0); }
    public static RateLimitDecision limited(long retryAfterSeconds) {
        return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
    }
}
