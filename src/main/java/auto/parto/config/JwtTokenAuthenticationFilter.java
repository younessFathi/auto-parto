package auto.parto.config;

import auto.parto.utils.JwtTokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
public class JwtTokenAuthenticationFilter implements WebFilter {
    public static final String HEADER_PREFIX = "Bearer ";
    private final JwtTokenGenerator jwtTokenGenerator;
    private final ApiErrorResponseWriter errorResponseWriter;

    public JwtTokenAuthenticationFilter(JwtTokenGenerator jwtTokenGenerator, ApiErrorResponseWriter errorResponseWriter){
        this.jwtTokenGenerator = jwtTokenGenerator;
        this.errorResponseWriter = errorResponseWriter;
    }

    @NotNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NotNull WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange.getRequest());
        if (token == null) {
            log.warn("Missing authentication token for protected endpoint: {}", path);
            return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED", "Authentication required");
        }

        try {
            if (this.jwtTokenGenerator.validateToken(token)) {
                Authentication authentication = this.jwtTokenGenerator.getAuthentication(token);
                log.info("Authentication successful for user: {}, Roles: {}",
                        authentication.getName(), authentication.getAuthorities());
                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            }
        } catch (RuntimeException exception) {
            log.warn("Rejected token with invalid canonical claims for path: {}", path);
        }

        log.warn("Invalid or expired token for path: {}", path);
        return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED", "Authentication failed");
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/")
                || path.equals("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars")
                || path.equals("/actuator/health");
    }

    private String resolveToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(HEADER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }

}
