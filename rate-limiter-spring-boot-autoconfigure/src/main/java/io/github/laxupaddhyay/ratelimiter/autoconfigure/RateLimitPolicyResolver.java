package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves effective bucket policies for a route and, when validated, an API client. */
@FunctionalInterface
public interface RateLimitPolicyResolver {
    RateLimitPolicies resolve(HttpServletRequest request, RateLimitIdentity identity);
}
