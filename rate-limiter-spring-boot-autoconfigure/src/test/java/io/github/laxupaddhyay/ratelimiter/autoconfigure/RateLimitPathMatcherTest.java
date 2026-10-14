package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPathMatcherTest {
    @Test
    void includesApiPathsButHonorsExclusions() {
        RateLimitPathMatcher matcher = new RateLimitPathMatcher(
                List.of("/api/**", "/internal/limited/**"), List.of("/api/public/**"));

        assertThat(matcher.matches("/api/v1/orders")).isTrue();
        assertThat(matcher.matches("/api/public/status")).isFalse();
        assertThat(matcher.matches("/internal/limited/job")).isTrue();
        assertThat(matcher.matches("/web/home")).isFalse();
    }
}
