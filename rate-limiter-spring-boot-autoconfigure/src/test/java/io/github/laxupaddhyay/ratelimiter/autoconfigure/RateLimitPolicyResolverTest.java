package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPolicyResolverTest {
    @Test
    void routeOverrideAppliesAndClientOverrideWinsForConfiguredBuckets() {
        RateLimiterProperties properties = new RateLimiterProperties();
        RateLimiterProperties.RoutePolicy route = new RateLimiterProperties.RoutePolicy();
        route.setPath("/api/heavy/**");
        route.setUser(policy(5));
        route.setApiKey(policy(10));
        properties.setRoutes(java.util.List.of(route));

        RateLimiterProperties.ClientPolicy client = new RateLimiterProperties.ClientPolicy();
        client.setApiKey(policy(3));
        properties.getClients().put("trusted-client", client);

        RateLimitPolicyResolver resolver = new ConfiguredRateLimitPolicyResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/heavy/report");
        RateLimitPolicies effective = resolver.resolve(request,
                new RateLimitIdentity(Optional.of("alice"), Optional.of("secret"), Optional.of("trusted-client")));

        assertThat(effective.user().getCapacity()).isEqualTo(5);
        assertThat(effective.apiKey().getCapacity()).isEqualTo(3);
    }

    @Test
    void unmatchedRouteUsesSharedDefaults() {
        RateLimiterProperties properties = new RateLimiterProperties();
        RateLimitPolicyResolver resolver = new ConfiguredRateLimitPolicyResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");

        RateLimitPolicies effective = resolver.resolve(request,
                new RateLimitIdentity(Optional.empty(), Optional.of("client-key")));

        assertThat(effective.user().getCapacity()).isEqualTo(60);
        assertThat(effective.apiKey().getCapacity()).isEqualTo(120);
    }

    private RateLimiterProperties.Policy policy(long capacity) {
        return new RateLimiterProperties.Policy(capacity, capacity, Duration.ofMinutes(1));
    }
}
