package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import java.util.Objects;

/** Non-secret client identity returned after API-key validation. */
public record ApiKeyDetails(String clientId) {
    public ApiKeyDetails {
        Objects.requireNonNull(clientId, "clientId");
        clientId = clientId.trim();
        if (clientId.isEmpty()) throw new IllegalArgumentException("clientId must not be blank");
    }
}
