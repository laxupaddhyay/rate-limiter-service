package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {
    private boolean enabled = true;
    @NotBlank private String apiKeyHeader = "X-API-Key";
    @NotBlank private String redisKeyPrefix = "rate-limit";
    @Valid @NotNull private Paths paths = new Paths();
    @Valid @NotNull private Policy user = new Policy(60, 60, Duration.ofMinutes(1));
    @Valid @NotNull private Policy apiKey = new Policy(120, 120, Duration.ofMinutes(1));
    @Valid @NotNull private List<RoutePolicy> routes = new ArrayList<>();
    @Valid @NotNull private java.util.Map<String, ClientPolicy> clients = new java.util.LinkedHashMap<>();
    private boolean requireApiKeyValidation;
    @Min(16) @Max(8192) private int apiKeyMaxLength = 512;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKeyHeader() { return apiKeyHeader; }
    public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
    public String getRedisKeyPrefix() { return redisKeyPrefix; }
    public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }
    public Paths getPaths() { return paths; }
    public void setPaths(Paths paths) { this.paths = paths; }
    public Policy getUser() { return user; }
    public void setUser(Policy user) { this.user = user; }
    public Policy getApiKey() { return apiKey; }
    public void setApiKey(Policy apiKey) { this.apiKey = apiKey; }
    public List<RoutePolicy> getRoutes() { return routes; }
    public void setRoutes(List<RoutePolicy> routes) { this.routes = routes; }
    public java.util.Map<String, ClientPolicy> getClients() { return clients; }
    public void setClients(java.util.Map<String, ClientPolicy> clients) { this.clients = clients; }
    public boolean isRequireApiKeyValidation() { return requireApiKeyValidation; }
    public void setRequireApiKeyValidation(boolean requireApiKeyValidation) { this.requireApiKeyValidation = requireApiKeyValidation; }
    public int getApiKeyMaxLength() { return apiKeyMaxLength; }
    public void setApiKeyMaxLength(int apiKeyMaxLength) { this.apiKeyMaxLength = apiKeyMaxLength; }
    @AssertTrue(message = "api-key-header must be a valid HTTP token")
    public boolean isValidApiKeyHeader() {
        if (apiKeyHeader == null || apiKeyHeader.isBlank()) return false;
        for (int i = 0; i < apiKeyHeader.length(); i++) {
            char character = apiKeyHeader.charAt(i);
            if (!Character.isLetterOrDigit(character) && "!#$%&'*+-.^_`|~".indexOf(character) < 0) return false;
        }
        return true;
    }

    public static class RoutePolicy {
        @NotBlank private String path;
        @Valid private Policy user;
        @Valid private Policy apiKey;
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public Policy getUser() { return user; }
        public void setUser(Policy user) { this.user = user; }
        public Policy getApiKey() { return apiKey; }
        public void setApiKey(Policy apiKey) { this.apiKey = apiKey; }
    }

    public static class ClientPolicy {
        @Valid private Policy user;
        @Valid private Policy apiKey;
        public Policy getUser() { return user; }
        public void setUser(Policy user) { this.user = user; }
        public Policy getApiKey() { return apiKey; }
        public void setApiKey(Policy apiKey) { this.apiKey = apiKey; }
    }

    public static class Paths {
        @NotNull private List<@NotBlank String> include = new ArrayList<>(List.of("/api/**"));
        @NotNull private List<@NotBlank String> exclude = new ArrayList<>(List.of("/actuator/health", "/actuator/info"));
        public List<String> getInclude() { return include; }
        public void setInclude(List<String> include) { this.include = include; }
        public List<String> getExclude() { return exclude; }
        public void setExclude(List<String> exclude) { this.exclude = exclude; }
    }

    public static class Policy {
        @Min(1) private long capacity;
        @Min(1) private long refillTokens;
        @NotNull private Duration refillPeriod;

        public Policy() { }
        public Policy(long capacity, long refillTokens, Duration refillPeriod) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriod = refillPeriod;
        }
        public long getCapacity() { return capacity; }
        public void setCapacity(long capacity) { this.capacity = capacity; }
        public long getRefillTokens() { return refillTokens; }
        public void setRefillTokens(long refillTokens) { this.refillTokens = refillTokens; }
        public Duration getRefillPeriod() { return refillPeriod; }
        public void setRefillPeriod(Duration refillPeriod) { this.refillPeriod = refillPeriod; }
        @AssertTrue(message = "refill-period must be at least one millisecond")
        public boolean isValidPeriod() { return refillPeriod != null && refillPeriod.toMillis() > 0; }
    }
}
