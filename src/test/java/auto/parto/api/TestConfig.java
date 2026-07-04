package auto.parto.api;

import auto.parto.service.RateLimitService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

@TestConfiguration
@EnableWebFluxSecurity
class TestConfig {

    @Bean
    @Primary
    public RateLimitService rateLimitService() {
        return new RateLimitService(null) {
            @Override
            public Mono<Boolean> isAllowed(String ip, int maxRequests, Duration window) {
                return Mono.just(true);
            }
        };
    }

    @Bean
    public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/auth/**").permitAll()
                        .anyExchange().authenticated()
                )
                .build();
    }
}
