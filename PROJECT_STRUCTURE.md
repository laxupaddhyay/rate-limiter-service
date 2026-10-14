# Architecture and project structure

## Modules

    rate-limiter-service
    ├── rate-limiter-spring-boot-autoconfigure
    │   ├── configuration properties and policy resolver
    │   ├── API-key identity and validation extension points
    │   ├── Spring Security configurer and servlet filter
    │   ├── Redis Lua token-bucket service
    │   ├── Micrometer metrics
    │   └── optional Actuator Redis health indicator
    ├── rate-limiter-spring-boot-starter
    │   └── dependency facade for consumer applications
    └── sample-app
        ├── Redis connection and timeout configuration
        ├── Basic authentication and API-key validation
        ├── example route and client policies
        └── readiness, metrics, and HTTP examples

The starter module supplies dependencies but contains no application policy. Auto-configuration provides defaults and backs off when an application supplies its own identity resolver, policy resolver, or rate-limit service.

## Request flow

1. Spring Security authenticates the request and installs its Authentication in the security context. The rate-limit filter runs after AnonymousAuthenticationFilter.
2. For a matching path, the filter checks that the configured API-key header appears at most once, is not blank, contains no whitespace/control characters, and is within the configured maximum length. Malformed headers receive 400.
3. RateLimitIdentityResolver extracts an authenticated non-anonymous user ID and an optional API-key identity.
4. If an ApiKeyValidator bean exists, the filter validates the presented key. A rejected key receives 401. An unavailable validation service receives 503. The validated key's ApiKeyDetails supplies a stable, non-secret client ID. Setting require-api-key-validation makes startup fail unless this hook is present.
5. RateLimitPolicyResolver selects effective user and API-key policies. It applies shared defaults, the first matching route override, then any configured client override.
6. RateLimitService submits applicable identities and policies to Redis. One Lua script uses Redis server time, computes refill for every bucket, and consumes only if every applicable bucket has at least one token.
7. The filter continues allowed requests. It returns 429 with Retry-After for a depleted bucket, or fail-closed 503 for Redis failures. Errors use application/problem+json.

Requests with a user and API key must pass both quotas. A missing bucket starts full. The Lua operation does not spend one bucket if the other denies the request.

## Policy resolution

RateLimiterProperties holds the shared user and API-key policies, path inclusions/exclusions, ordered route rules, client policy map, API-key header controls, and validation requirement. A bucket policy consists of capacity, refill token count, and refill period.

ConfiguredRateLimitPolicyResolver starts with the shared defaults. It selects the first matching route rule and replaces only the user/API-key fields that rule defines. It then applies a matching client policy, again replacing only its configured fields. Put narrow route patterns before broad route patterns.

The client ID is provided by a successful API-key validator. Per-client settings choose policy parameters for each presented key mapped to that client. Bucket state remains keyed to each individual API key; keys for one client do not currently share one aggregate bucket. Implement a custom RateLimitPolicyResolver for database-backed, tenant-aware, or dynamically refreshed policies.

## API-key validation

ApiKeyValidator is a functional extension point. It accepts the presented key and returns Optional<ApiKeyDetails>; ApiKeyDetails contains a stable client ID but no secret. The filter keeps using the key value only as the API-key bucket identity, and Redis receives only its SHA-256 digest.

The sample validator compares a single key loaded from the environment using a constant-time byte comparison. Production applications should validate against a one-way hash in their credential store, check revocation/expiry, and return a stable client ID. Avoid logging key values. A validator that cannot reach its backing store should throw ApiKeyValidationUnavailableException so the filter can return a structured 503.

When no validator bean is configured, API keys remain identity-only unless require-api-key-validation is true. This supports deployments where an upstream gateway already authenticates keys while giving applications a direct validation hook.

## Redis data model and atomic operation

There is no SQL schema. For each request identity, Redis stores one hash:

    <prefix>:user:<sha256("user:" + user-id)>
    <prefix>:api-key:<sha256("api-key:" + presented-key)>

Each hash has these fields:

| Field | Meaning |
| --- | --- |
| tokens | Fractional number of tokens remaining after successful requests. |
| last_refill_ms | Redis-server epoch time in milliseconds of the last successful consumption. |

The Lua script reads Redis TIME. It refills to the selected policy capacity, checks all supplied buckets, and only writes token deductions if all checks pass. Keys expire after the maximum time required to refill from empty, with a small cushion. This bounds stale-key growth for high-cardinality identities. Policy changes cap stored tokens at the new capacity when the next request arrives.

Dual-identity scripts require standalone Redis in this version. Redis Cluster may reject a script whose user and API-key keys occupy different hash slots; cluster support requires a different key strategy or an explicit multi-step consistency tradeoff.

## Security and failure behavior

- API-key header values are never used as metric tags and are not written directly to Redis keys.
- Duplicate, blank, overlong, whitespace-containing, and control-character API-key headers are rejected with 400.
- Missing identity and invalid API credentials receive 401; API-key validation errors do not disclose whether a particular key exists.
- Redis and API-key validation backend outages fail closed with 503.
- Redis keys expire automatically. High-cardinality tests exercise distinct identity creation and verify expiry is applied.
- The sample exposes local demo credentials only through .env.example placeholders. Actual .env files are ignored by Git.

## Operations and observability

Spring Data Redis connection and command timeouts are configured with spring.data.redis.connect-timeout and spring.data.redis.timeout. The sample maps REDIS_CONNECT_TIMEOUT and REDIS_COMMAND_TIMEOUT into those properties.

When Actuator is present, the auto-configuration publishes the redisRateLimiter health indicator. The sample includes it in readiness and excludes it from liveness, so an unavailable Redis makes a pod unready without triggering liveness restarts.

Metrics avoid identity and client tags:

| Metric | Tags and purpose |
| --- | --- |
| rate_limiter.requests | outcome and identity_type; request allow/deny/missing/invalid/failure counts. |
| rate_limiter.redis.commands | outcome; Redis Lua commands allowed, limited, or failed. |
| rate_limiter.redis.command.duration | operation; Redis token-bucket command latency. |

## Design patterns and principles

- Auto-configuration with conditional bean registration lets applications replace defaults without forking the starter.
- The starter facade offers one Maven dependency while keeping the implementation module independently understandable.
- Strategy and dependency inversion are provided by RateLimitIdentityResolver, ApiKeyValidator, RateLimitPolicyResolver, and RateLimitService.
- Spring Security DSL integration keeps the servlet filter inside the security chain and orders it after authentication.
- The Lua script is an atomic transaction boundary for refill, dual-bucket evaluation, and consumption.
- The filter owns HTTP status and problem response behavior; the Redis service owns bucket math/storage; policy and identity resolution are separate strategies.
- Fail-closed behavior, bounded metric tags, hashed key names, and TTL expiry support secure operation without adding raw identity data to telemetry.

## Tests

Unit tests cover configuration validation, identity resolution, path matching, route/client policy precedence, and sample HTTP response behavior. Testcontainers integration tests cover burst capacity, refill/retry timing, concurrency, dual-identity atomicity, bucket expiry, Redis restart/reconnect, command metrics, health, and many unique identities. Container tests require Docker and are skipped when Docker is not available.
