package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import java.util.Optional;

/** Validates the presented API key and returns a stable, non-secret client identifier. */
@FunctionalInterface
public interface ApiKeyValidator {
    Optional<ApiKeyDetails> validate(String presentedApiKey);
}
