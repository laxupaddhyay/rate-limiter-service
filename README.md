# Redis Rate Limiter Spring Boot Starter

A reusable Spring Boot starter and sample consumer for Redis-backed token-bucket rate limiting by authenticated user and API key.

## Capabilities

- Uses a Redis Lua script to atomically refill and consume user and API-key buckets. If either bucket denies a request, neither bucket is consumed.
- Supports shared defaults, path-specific overrides, and API-client-specific overrides.
- Provides an API-key validation extension point. The sample validates a configured demo key and maps it to a stable client ID.
- Hashes rate-limit identities before putting them in Redis keys. Raw API keys are not logged or stored in Redis keys.
- Returns structured problem responses: 400 for malformed API-key headers, 401 for missing or invalid identities, 429 with Retry-After for rate limits, and 503 when Redis or API-key validation is unavailable.
- Exposes Redis readiness through Spring Boot Actuator and low-cardinality request and Redis command metrics.

The project uses Java 21, Maven, Spring Boot 3.5.16, Spring Security, and standalone Redis. It has no SQL database. Redis Cluster is not supported because one atomic Lua operation needs to access both identity buckets.

## Run the sample

1. Install Java 21, Maven, and Docker Compose.
2. Copy the environment template and set Redis and demo credentials:

       Copy-Item .env.example .env

   The .env file is ignored by Git. The demo key and password are local sample credentials; replace them before running.
3. Start Redis:

       docker compose up -d redis

4. Start the application from the project root:

       mvn -pl sample-app spring-boot:run

5. Call the sample endpoint with the Basic-auth user or configured API key:

       curl.exe -u demo:your-configured-password http://localhost:8080/api/hello
       curl.exe -H "X-API-Key: your-configured-demo-key" http://localhost:8080/api/hello

The sample rejects API keys that do not match DEMO_API_KEY and maps valid keys to the demo-client client profile. Its API keys are stored in the local environment, not in source files. The sample key validator is illustrative; production applications should look up a one-way hash of the presented key in their own credential store.

The sample policy applies 60 tokens per minute to each user and 120 per minute to each API key. The demo-client API-key policy is 30 per minute. Requests to /api/heavy/** use a route-specific policy. The sample exposes health, readiness, info, and metrics endpoints under /actuator.

Stop Redis when finished:

       docker compose down

## Add the starter to another application

Install the modules to the local Maven repository:

       mvn -pl rate-limiter-spring-boot-starter -am install

Add the starter dependency to a Spring Boot servlet application that uses Spring Security:

    <dependency>
        <groupId>io.github.laxupaddhyay.ratelimiter</groupId>
        <artifactId>rate-limiter-spring-boot-starter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

Configure a RedisConnectionFactory through Spring Boot Redis properties, then attach the configurer to the security chain:

    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http,
                                            RateLimiterHttpConfigurer rateLimiter) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .with(rateLimiter, Customizer.withDefaults());
        return http.build();
    }

The filter is installed after AnonymousAuthenticationFilter so it can read authenticated principals. A custom authentication filter must populate the SecurityContext before that point.

### Validate API keys

Define an ApiKeyValidator bean to check credentials and return a stable, non-secret client ID:

    @Bean
    ApiKeyValidator apiKeyValidator(ApiKeyStore keyStore) {
        return presentedKey -> keyStore.findActiveByHash(sha256(presentedKey))
                .map(key -> new ApiKeyDetails(key.clientId()));
    }

Set rate-limiter.require-api-key-validation to true to fail startup if no validator bean is configured. Without a validator and with that option disabled, the header is used only as a rate-limit identity, preserving gateway-validated deployments. Do not log the presented key. If a validator backend is temporarily unavailable, throw ApiKeyValidationUnavailableException; the filter fails closed with 503.

Applications can also replace RateLimitIdentityResolver or RateLimitPolicyResolver with their own beans.

## Policies

Configure shared defaults, route overrides, and client overrides in application.yml:

    rate-limiter:
      user:
        capacity: 60
        refill-tokens: 60
        refill-period: 1m
      api-key:
        capacity: 120
        refill-tokens: 120
        refill-period: 1m
      routes:
        - path: /api/bulk/**
          user:
            capacity: 10
            refill-tokens: 10
            refill-period: 1m
          api-key:
            capacity: 20
            refill-tokens: 20
            refill-period: 1m
      clients:
        billing-partner:
          api-key:
            capacity: 500
            refill-tokens: 500
            refill-period: 1m

The first matching route entry wins, so place more specific paths before broader paths. A route only overrides the bucket policy entries it defines. A client override is applied after the route override and replaces only the policy fields configured for that client. The client ID comes from the validated ApiKeyDetails. Each API key retains its own bucket state; a client profile selects the policy for that key and does not combine several keys into one shared client bucket.

Other settings include rate-limiter.paths.include and paths.exclude, api-key-header, api-key-max-length, redis-key-prefix, enabled, and require-api-key-validation. Matching requests need an authenticated user, a valid API key, or both. When both are present, both configured bucket policies apply.

## Operations and metrics

Redis failures are fail-closed. The starter contributes a redisRateLimiter health indicator when Actuator is present. The sample includes it in /actuator/health/readiness and leaves it out of liveness.

Set connection and command timeouts using Spring Data Redis properties:

    spring.data.redis.connect-timeout: 2s
    spring.data.redis.timeout: 2s

The sample maps REDIS_CONNECT_TIMEOUT and REDIS_COMMAND_TIMEOUT from .env to these properties. Configure the same Spring Data Redis properties in consumer applications. The readiness probe pings Redis and reports status only; it does not expose credentials or connection details.

Metrics use bounded tags and never include identity values:

- rate_limiter.requests counts allowed, limited, missing identity, malformed header, invalid key, Redis failure, and validator failure outcomes by identity type.
- rate_limiter.redis.commands counts allowed, limited, and failed Lua commands.
- rate_limiter.redis.command.duration measures the Redis token-bucket command.

The sample exposes these metrics at /actuator/metrics/rate_limiter.requests and /actuator/metrics/rate_limiter.redis.command.duration.

## Build and tests

Run the full build:

       mvn clean verify

Unit tests cover identity handling, policy selection, path matching, and configuration validation. Sample tests cover filter placement, API-key validation, malformed and oversized headers, and 401, 429, and 503 responses. Redis Testcontainers tests cover burst capacity, refill and retry timing, concurrency, all-or-none dual identity checks, expiry, Redis restart/reconnect, metrics, and many distinct identities. Docker must be available to run those tests; they are skipped when Docker is unavailable.

## Modules

- rate-limiter-spring-boot-autoconfigure contains validated properties, public extension points, the Spring Security filter/configurer, Redis Lua implementation, metrics, and optional Actuator health integration.
- rate-limiter-spring-boot-starter provides the consumer dependency.
- sample-app demonstrates Redis configuration, Basic authentication, API-key validation, route/client policies, readiness, and metrics.
- compose.yml starts a local standalone Redis instance with persistent storage.
