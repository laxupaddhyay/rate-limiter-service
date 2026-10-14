package io.github.laxupaddhyay.ratelimiter.sample;

import io.github.laxupaddhyay.ratelimiter.autoconfigure.RateLimitDecision;
import io.github.laxupaddhyay.ratelimiter.autoconfigure.RateLimitPolicies;
import io.github.laxupaddhyay.ratelimiter.autoconfigure.RateLimitService;
import org.springframework.boot.test.context.TestConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SampleRateLimitFilterTest.TestOverrides.class)
@Timeout(value = 15, unit = java.util.concurrent.TimeUnit.SECONDS)
class SampleRateLimitFilterTest {
    @Autowired MockMvc mvc;
    @Autowired StubRateLimitService rateLimitService;

    @Test
    void filterRunsAfterAuthenticationAndAllowsAuthenticatedPrincipal() throws Exception {
        rateLimitService.set(identity -> RateLimitDecision.pass());

        mvc.perform(get("/api/hello").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"Hello from the Redis rate-limited API.\"}"));
    }

    @Test
    void matchingRequestWithoutIdentityReturnsProblem401() throws Exception {
        mvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void limitedRequestReturns429AndRetryHeader() throws Exception {
        rateLimitService.set(identity -> RateLimitDecision.limited(9));

        mvc.perform(get("/api/hello").header("X-API-Key", "sample-demo-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "9"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void redisFailureReturns503Problem() throws Exception {
        rateLimitService.set(identity -> { throw new DataAccessResourceFailureException("redis down"); });

        mvc.perform(get("/api/hello").with(user("alice")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void invalidApiKeyReturnsUnauthorizedWithoutCallingLimiter() throws Exception {
        mvc.perform(get("/api/hello").header("X-API-Key", "not-valid"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void duplicateApiKeyHeadersAreRejectedAsMalformed() throws Exception {
        mvc.perform(get("/api/hello").header("X-API-Key", "sample-demo-key", "second-key"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void oversizedApiKeyHeaderIsRejectedAsMalformed() throws Exception {
        mvc.perform(get("/api/hello").header("X-API-Key", "x".repeat(513)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {
        @Bean @Primary StubRateLimitService stubRateLimitService() { return new StubRateLimitService(); }
    }

    static class StubRateLimitService implements RateLimitService {
        private final AtomicReference<java.util.function.BiFunction<io.github.laxupaddhyay.ratelimiter.autoconfigure.RateLimitIdentity, RateLimitPolicies,
                RateLimitDecision>> action = new AtomicReference<>((identity, policies) -> RateLimitDecision.pass());
        void set(java.util.function.Function<io.github.laxupaddhyay.ratelimiter.autoconfigure.RateLimitIdentity,
                RateLimitDecision> next) { action.set((identity, policies) -> next.apply(identity)); }
        @Override public RateLimitDecision check(io.github.laxupaddhyay.ratelimiter.autoconfigure.RateLimitIdentity identity,
                                                 RateLimitPolicies policies) {
            return action.get().apply(identity, policies);
        }
    }
}
