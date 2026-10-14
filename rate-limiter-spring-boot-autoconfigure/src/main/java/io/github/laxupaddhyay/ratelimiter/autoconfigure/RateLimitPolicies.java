package io.github.laxupaddhyay.ratelimiter.autoconfigure;

/** The effective user and API-key bucket policies for one request. */
public record RateLimitPolicies(RateLimiterProperties.Policy user, RateLimiterProperties.Policy apiKey) { }
