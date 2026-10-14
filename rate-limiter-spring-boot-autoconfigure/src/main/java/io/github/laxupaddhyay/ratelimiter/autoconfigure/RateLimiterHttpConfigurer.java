package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** Attach with {@code http.with(rateLimiterHttpConfigurer, Customizer.withDefaults())}. */
public final class RateLimiterHttpConfigurer extends AbstractHttpConfigurer<RateLimiterHttpConfigurer, HttpSecurity> {
    private final RateLimitService rateLimitService;
    private final RateLimitIdentityResolver identityResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final ApiKeyValidator apiKeyValidator;
    private final RateLimiterProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    RateLimiterHttpConfigurer(RateLimitService rateLimitService, RateLimitIdentityResolver identityResolver,
                              RateLimitPolicyResolver policyResolver, ApiKeyValidator apiKeyValidator,
                              RateLimiterProperties properties, ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.rateLimitService = rateLimitService;
        this.identityResolver = identityResolver;
        this.policyResolver = policyResolver;
        this.apiKeyValidator = apiKeyValidator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void configure(HttpSecurity http) {
        RateLimitPathMatcher matcher = new RateLimitPathMatcher(
                properties.getPaths().getInclude(), properties.getPaths().getExclude());
        http.addFilterAfter(new RateLimitFilter(rateLimitService, identityResolver, policyResolver,
                        apiKeyValidator, properties, matcher, objectMapper, meterRegistry),
                AnonymousAuthenticationFilter.class);
    }
}
