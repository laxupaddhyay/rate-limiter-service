package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

final class ConfiguredRateLimitPolicyResolver implements RateLimitPolicyResolver {
    private final RateLimiterProperties properties;
    private final List<Route> routes;

    ConfiguredRateLimitPolicyResolver(RateLimiterProperties properties) {
        this.properties = properties;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.routes = properties.getRoutes().stream()
                .map(route -> new Route(parser.parse(route.getPath()), route))
                .toList();
    }

    @Override
    public RateLimitPolicies resolve(HttpServletRequest request, RateLimitIdentity identity) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        RateLimiterProperties.Policy user = properties.getUser();
        RateLimiterProperties.Policy apiKey = properties.getApiKey();
        PathContainer container = PathContainer.parsePath(path);
        for (Route route : routes) {
            if (route.pattern.matches(container)) {
                if (route.policy.getUser() != null) user = route.policy.getUser();
                if (route.policy.getApiKey() != null) apiKey = route.policy.getApiKey();
                break;
            }
        }

        if (identity.clientId().isPresent()) {
            Map<String, RateLimiterProperties.ClientPolicy> clients = properties.getClients();
            RateLimiterProperties.ClientPolicy client = clients.get(identity.clientId().get());
            if (client != null) {
                if (client.getUser() != null) user = client.getUser();
                if (client.getApiKey() != null) apiKey = client.getApiKey();
            }
        }
        return new RateLimitPolicies(user, apiKey);
    }

    private record Route(PathPattern pattern, RateLimiterProperties.RoutePolicy policy) { }
}
