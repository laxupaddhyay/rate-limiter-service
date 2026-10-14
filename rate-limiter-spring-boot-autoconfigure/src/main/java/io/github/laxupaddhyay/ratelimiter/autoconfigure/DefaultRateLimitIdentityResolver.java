package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

final class DefaultRateLimitIdentityResolver implements RateLimitIdentityResolver {
    private final String apiKeyHeader;

    DefaultRateLimitIdentityResolver(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }

    @Override
    public RateLimitIdentity resolve(HttpServletRequest request, Authentication authentication) {
        Optional<String> user = Optional.empty();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails details) {
                user = Optional.ofNullable(details.getUsername());
            } else if (principal instanceof String name && !"anonymousUser".equals(name)) {
                user = Optional.of(name);
            } else if (principal != null) {
                user = Optional.ofNullable(authentication.getName());
            }
        }
        return new RateLimitIdentity(user, Optional.ofNullable(request.getHeader(apiKeyHeader)));
    }
}
