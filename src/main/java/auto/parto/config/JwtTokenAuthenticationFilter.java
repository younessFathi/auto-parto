package auto.parto.config;

import auto.parto.utils.JwtTokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
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

    public JwtTokenAuthenticationFilter(JwtTokenGenerator jwtTokenGenerator){
        this.jwtTokenGenerator = jwtTokenGenerator;
    }

    @NotNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NotNull WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. Skip JWT check for public endpoints
        if (path.startsWith("/auth/") || path.startsWith("/v3/api-docs")) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange.getRequest());
        if (StringUtils.hasText(token) && this.jwtTokenGenerator.validateToken(token)) {
            Authentication authentication = this.jwtTokenGenerator.getAuthentication(token);
            log.info("Authentication successful for user: {}, Roles: {}",
                    authentication.getName(), authentication.getAuthorities());
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        }
        log.info("Unauthorized token");
        return chain.filter(exchange);
    }

    private String resolveToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(HEADER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }

}
