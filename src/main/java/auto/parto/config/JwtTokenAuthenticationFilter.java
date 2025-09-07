package auto.parto.config;

import auto.parto.utils.JwtTokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class JwtTokenAuthenticationFilter implements WebFilter {
    public static final String HEADER_PREFIX = "Bearer ";
    private final JwtTokenGenerator jwtTokenGenerator;

    public JwtTokenAuthenticationFilter(JwtTokenGenerator jwtTokenGenerator){
        this.jwtTokenGenerator = jwtTokenGenerator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = resolveToken(exchange.getRequest());
        log.info("Token {} " , token);
        log.info("Token is valid {} " , this.jwtTokenGenerator.validateToken(token));
        log.info("StringUtils.hasText(token)  {} " , StringUtils.hasText(token));
        if (StringUtils.hasText(token) && this.jwtTokenGenerator.validateToken(token)) {
            return Mono.fromCallable(() -> this.jwtTokenGenerator.getAuthentication(token))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(authentication -> chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)));
        }
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
