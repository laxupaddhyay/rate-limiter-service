package io.github.laxupaddhyay.ratelimiter.sample;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class SampleRedisConfiguration {
    @Bean
    LettuceConnectionFactory redisConnectionFactory(
            @Value("${REDIS_URL:redis://localhost:6379}") String redisUrl,
            @Value("${REDIS_USERNAME:}") String configuredUsername,
            @Value("${REDIS_PASSWORD:}") String configuredPassword,
            @Value("${spring.data.redis.timeout:2s}") Duration commandTimeout,
            @Value("${spring.data.redis.connect-timeout:2s}") Duration connectTimeout) {
        URI uri = URI.create(redisUrl);
        if (!"redis".equalsIgnoreCase(uri.getScheme()) && !"rediss".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("REDIS_URL must use redis:// or rediss:// and target standalone Redis");
        }
        if (uri.getHost() == null) throw new IllegalArgumentException("REDIS_URL must include a Redis host");

        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(uri.getHost(),
                uri.getPort() < 0 ? 6379 : uri.getPort());
        if (uri.getPath() != null && uri.getPath().length() > 1) {
            server.setDatabase(Integer.parseInt(uri.getPath().substring(1)));
        }

        String urlUsername = null;
        String urlPassword = null;
        if (uri.getUserInfo() != null) {
            String[] userInfo = uri.getUserInfo().split(":", 2);
            urlUsername = userInfo.length == 2 ? userInfo[0] : null;
            urlPassword = userInfo.length == 2 ? userInfo[1] : userInfo[0];
        }
        String username = hasText(configuredUsername) ? configuredUsername : urlUsername;
        String password = hasText(configuredPassword) ? configuredPassword : urlPassword;
        if (hasText(username)) server.setUsername(username);
        if (hasText(password)) server.setPassword(RedisPassword.of(password));

        LettuceClientConfiguration.LettuceClientConfigurationBuilder client = LettuceClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build()).build());
        if ("rediss".equalsIgnoreCase(uri.getScheme())) client.useSsl();
        return new LettuceConnectionFactory(server, client.build());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
