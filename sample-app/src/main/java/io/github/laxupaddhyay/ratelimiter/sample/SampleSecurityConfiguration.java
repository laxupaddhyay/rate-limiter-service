package io.github.laxupaddhyay.ratelimiter.sample;

import io.github.laxupaddhyay.ratelimiter.autoconfigure.RateLimiterHttpConfigurer;
import io.github.laxupaddhyay.ratelimiter.autoconfigure.ApiKeyDetails;
import io.github.laxupaddhyay.ratelimiter.autoconfigure.ApiKeyValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class SampleSecurityConfiguration {
    @Bean
    SecurityFilterChain sampleSecurityFilterChain(HttpSecurity http, RateLimiterHttpConfigurer rateLimiter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .with(rateLimiter, Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder samplePasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService sampleUsers(@Value("${sample.auth.username}") String username,
                                   @Value("${sample.auth.password}") String password,
                                   PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password)).roles("USER").build());
    }

    @Bean
    ApiKeyValidator sampleApiKeyValidator(@Value("${sample.auth.api-key}") String expectedApiKey) {
        if (expectedApiKey.isBlank()) throw new IllegalStateException("DEMO_API_KEY must not be blank");
        byte[] expected = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        return presentedApiKey -> MessageDigest.isEqual(
                presentedApiKey.getBytes(StandardCharsets.UTF_8), expected)
                ? Optional.of(new ApiKeyDetails("demo-client"))
                : Optional.empty();
    }
}
