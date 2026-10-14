package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitIdentityResolverTest {
    private final DefaultRateLimitIdentityResolver resolver = new DefaultRateLimitIdentityResolver("X-API-Key");

    @Test
    void resolvesAuthenticatedPrincipalAndApiKey() {
        HttpServletRequest request = request(" key-123 ");
        var authentication = new TestingAuthenticationToken("alice", "secret", "ROLE_USER");

        RateLimitIdentity identity = resolver.resolve(request, authentication);

        assertThat(identity.userId()).contains("alice");
        assertThat(identity.apiKey()).contains("key-123");
    }

    @Test
    void ignoresAnonymousPrincipalAndBlankApiKey() {
        var authentication = new AnonymousAuthenticationToken("test", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        RateLimitIdentity identity = resolver.resolve(request("  "), authentication);

        assertThat(identity.userId()).isEmpty();
        assertThat(identity.apiKey()).isEmpty();
        assertThat(identity.isEmpty()).isTrue();
    }

    @Test
    void supportsApiKeyOnlyRequests() {
        RateLimitIdentity identity = resolver.resolve(request("client-key"), null);
        assertThat(identity.userId()).isEqualTo(Optional.empty());
        assertThat(identity.apiKey()).contains("client-key");
    }

    private MockHttpServletRequest request(String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", apiKey);
        return request;
    }
}
