package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import java.util.Optional;

/** Contains identifiers only; API keys are never intended for logging or persistence. */
public record RateLimitIdentity(Optional<String> userId, Optional<String> apiKey, Optional<String> clientId) {
    public RateLimitIdentity(Optional<String> userId, Optional<String> apiKey) {
        this(userId, apiKey, Optional.empty());
    }

    public RateLimitIdentity {
        userId = userId == null ? Optional.empty() : userId.filter(value -> !value.isBlank());
        apiKey = apiKey == null ? Optional.empty() : apiKey.map(String::trim).filter(value -> !value.isBlank());
        clientId = clientId == null ? Optional.empty() : clientId.map(String::trim).filter(value -> !value.isBlank());
    }
    public boolean isEmpty() { return userId.isEmpty() && apiKey.isEmpty(); }
}
