package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterPropertiesTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsHaveExpectedPoliciesAndPaths() {
        RateLimiterProperties properties = new RateLimiterProperties();

        assertThat(validator.validate(properties)).isEmpty();
        assertThat(properties.getPaths().getInclude()).containsExactly("/api/**");
        assertThat(properties.getUser().getCapacity()).isEqualTo(60);
        assertThat(properties.getApiKey().getCapacity()).isEqualTo(120);
    }

    @Test
    void rejectsInvalidBucketsAndHeader() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setApiKeyHeader(" ");
        properties.getUser().setCapacity(0);
        properties.getApiKey().setRefillPeriod(java.time.Duration.ZERO);

        assertThat(validator.validate(properties)).hasSizeGreaterThanOrEqualTo(3);
    }
}
