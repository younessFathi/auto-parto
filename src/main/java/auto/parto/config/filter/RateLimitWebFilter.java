package auto.parto.config.filter;

import auto.parto.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitWebFilter implements WebFilter {

    private static final String RATE_LIMIT_EXCEEDED = "Rate limit exceeded. Try again later.";
    private static final int AUTH_MAX_REQUESTS = 5;
    private static final Duration AUTH_WINDOW = Duration.ofMinutes(1);

    private final RateLimitService rateLimitService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (!path.startsWith("/auth/")) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange);

        return rateLimitService.isAllowed(clientIp, AUTH_MAX_REQUESTS, AUTH_WINDOW)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    } else {
                        log.warn("Rate limit exceeded for IP: {}", clientIp);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                        String body = "{\"error\":\"" + RATE_LIMIT_EXCEEDED + "\"}";
                        return exchange.getResponse()
                                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
                    }
                });
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}