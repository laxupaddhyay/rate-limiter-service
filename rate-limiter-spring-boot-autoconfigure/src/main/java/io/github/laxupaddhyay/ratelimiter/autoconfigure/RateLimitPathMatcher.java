package io.github.laxupaddhyay.ratelimiter.autoconfigure;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

final class RateLimitPathMatcher {
    private final List<PathPattern> includes;
    private final List<PathPattern> excludes;

    RateLimitPathMatcher(List<String> includes, List<String> excludes) {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.includes = includes.stream().map(parser::parse).toList();
        this.excludes = excludes.stream().map(parser::parse).toList();
    }

    boolean matches(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return excludes.stream().noneMatch(pattern -> pattern.matches(container))
                && includes.stream().anyMatch(pattern -> pattern.matches(container));
    }
}
