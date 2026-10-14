package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class, HttpSecurity.class})
@ConditionalOnProperty(prefix = "rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    RateLimitIdentityResolver rateLimitIdentityResolver(RateLimiterProperties properties) {
        return new DefaultRateLimitIdentityResolver(properties.getApiKeyHeader());
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitPolicyResolver.class)
    RateLimitPolicyResolver rateLimitPolicyResolver(RateLimiterProperties properties) {
        return new ConfiguredRateLimitPolicyResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitService.class)
    RateLimitService rateLimitService(StringRedisTemplate redis, RateLimiterProperties properties,
                                      ObjectProvider<MeterRegistry> meterRegistries) {
        return new RedisTokenBucketRateLimiter(redis, properties,
                meterRegistries.getIfAvailable(() -> io.micrometer.core.instrument.Metrics.globalRegistry));
    }

    @Bean
    @ConditionalOnMissingBean
    RateLimiterHttpConfigurer rateLimiterHttpConfigurer(RateLimitService rateLimitService,
                                                         RateLimitIdentityResolver identityResolver,
                                                         RateLimitPolicyResolver policyResolver,
                                                         ObjectProvider<ApiKeyValidator> apiKeyValidators,
                                                         RateLimiterProperties properties,
                                                         ObjectProvider<ObjectMapper> objectMappers,
                                                         ObjectProvider<MeterRegistry> meterRegistries) {
        ApiKeyValidator apiKeyValidator = apiKeyValidators.getIfAvailable();
        if (properties.isRequireApiKeyValidation() && apiKeyValidator == null) {
            throw new IllegalStateException("rate-limiter.require-api-key-validation=true requires an ApiKeyValidator bean");
        }
        return new RateLimiterHttpConfigurer(rateLimitService, identityResolver, policyResolver,
                apiKeyValidator, properties,
                objectMappers.getIfAvailable(ObjectMapper::new),
                meterRegistries.getIfAvailable(() -> io.micrometer.core.instrument.Metrics.globalRegistry));
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class ActuatorConfiguration {
        @Bean(name = "redisRateLimiter")
        @ConditionalOnMissingBean(name = "redisRateLimiter")
        org.springframework.boot.actuate.health.HealthIndicator redisRateLimiterHealthIndicator(
                StringRedisTemplate redis) {
            return new RedisRateLimiterHealthIndicator(redis);
        }
    }
}
