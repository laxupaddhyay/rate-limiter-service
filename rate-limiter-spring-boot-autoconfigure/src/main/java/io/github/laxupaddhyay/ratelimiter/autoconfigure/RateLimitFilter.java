package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;

final class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitService rateLimitService;
    private final RateLimitIdentityResolver identityResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final ApiKeyValidator apiKeyValidator;
    private final RateLimiterProperties properties;
    private final RateLimitPathMatcher pathMatcher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    RateLimitFilter(RateLimitService rateLimitService, RateLimitIdentityResolver identityResolver,
                    RateLimitPolicyResolver policyResolver, ApiKeyValidator apiKeyValidator,
                    RateLimiterProperties properties,
                    RateLimitPathMatcher pathMatcher, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.rateLimitService = rateLimitService;
        this.identityResolver = identityResolver;
        this.policyResolver = policyResolver;
        this.apiKeyValidator = apiKeyValidator;
        this.properties = properties;
        this.pathMatcher = pathMatcher;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !pathMatcher.matches(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isValidApiKeyHeader(request)) {
            count("malformed_api_key_header", "none");
            writeProblem(request, response, HttpStatus.BAD_REQUEST, "Malformed API key header",
                    "The API-key header must occur once and contain a valid key.");
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        RateLimitIdentity identity = identityResolver.resolve(request, authentication);
        if (identity != null && identity.apiKey().isPresent() && apiKeyValidator != null) {
            try {
                var details = apiKeyValidator.validate(identity.apiKey().get());
                if (details == null || details.isEmpty()) {
                    count("invalid_api_key", "api_key");
                    writeProblem(request, response, HttpStatus.UNAUTHORIZED, "Invalid API key",
                            "The provided API key is not valid.");
                    return;
                }
                identity = new RateLimitIdentity(identity.userId(), identity.apiKey(),
                        java.util.Optional.of(details.get().clientId()));
            } catch (ApiKeyValidationUnavailableException exception) {
                count("api_key_validation_failure", "api_key");
                writeProblem(request, response, HttpStatus.SERVICE_UNAVAILABLE, "API key validation unavailable",
                        "The API key could not be checked because the validation service is unavailable.");
                return;
            }
        }
        if (identity == null || identity.isEmpty()) {
            count("missing_identity", "none");
            writeProblem(request, response, HttpStatus.UNAUTHORIZED, "Rate limit identity required",
                    "Provide an authenticated user or an API key.");
            return;
        }

        String identityType = identity.userId().isPresent()
                ? identity.apiKey().isPresent() ? "user_and_api_key" : "user"
                : "api_key";
        RateLimitDecision decision;
        try {
            decision = rateLimitService.check(identity, policyResolver.resolve(request, identity));
        } catch (DataAccessException exception) {
            count("redis_failure", identityType);
            writeProblem(request, response, HttpStatus.SERVICE_UNAVAILABLE, "Rate limiter unavailable",
                    "The request could not be checked because the rate-limit store is unavailable.");
            return;
        }
        if (!decision.allowed()) {
            count("limited", identityType);
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            writeProblem(request, response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded",
                    "Try again after " + decision.retryAfterSeconds() + " seconds.");
            return;
        }
        count("allowed", identityType);
        chain.doFilter(request, response);
    }

    private void count(String outcome, String identityType) {
        meterRegistry.counter("rate_limiter.requests", "outcome", outcome, "identity_type", identityType).increment();
    }

    private boolean isValidApiKeyHeader(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(properties.getApiKeyHeader());
        if (values == null || !values.hasMoreElements()) return true;
        String value = values.nextElement();
        if (values.hasMoreElements() || value == null || value.isBlank()
                || value.length() > properties.getApiKeyMaxLength() || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isWhitespace(character) || Character.isISOControl(character)) return false;
        }
        return true;
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                              String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
