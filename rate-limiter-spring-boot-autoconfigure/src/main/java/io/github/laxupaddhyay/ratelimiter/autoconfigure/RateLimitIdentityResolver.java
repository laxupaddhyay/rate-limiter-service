package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/** Extension point for mapping a request to its rate-limit identities. */
@FunctionalInterface
public interface RateLimitIdentityResolver {
    RateLimitIdentity resolve(HttpServletRequest request, Authentication authentication);
}
