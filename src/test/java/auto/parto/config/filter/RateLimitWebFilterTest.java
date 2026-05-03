package auto.parto.config.filter;

import auto.parto.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitWebFilterTest {

    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private WebFilterChain chain;

    @Test
    void filter_allowsNonAuthPaths() {
        RateLimitWebFilter filter = new RateLimitWebFilter(rateLimitService);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pieces").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();
    }

    @Test
    void filter_allowsRequest_whenUnderLimit() {
        RateLimitWebFilter filter = new RateLimitWebFilter(rateLimitService);
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/login")
                .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitService.isAllowed(anyString(), anyInt(), any(Duration.class))).thenReturn(Mono.just(true));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();
    }

    @Test
    void filter_blocksRequest_whenOverLimit() {
        RateLimitWebFilter filter = new RateLimitWebFilter(rateLimitService);
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/validate-otp")
                .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitService.isAllowed(anyString(), anyInt(), any(Duration.class))).thenReturn(Mono.just(false));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(429);
    }

    @Test
    void filter_extractsClientIp_fromXForwardedFor() {
        RateLimitWebFilter filter = new RateLimitWebFilter(rateLimitService);
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/login")
                .header("X-Forwarded-For", "10.0.0.1, 192.168.1.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitService.isAllowed(eq("10.0.0.1"), anyInt(), any(Duration.class))).thenReturn(Mono.just(true));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();
    }

    @Test
    void filter_allowsAuthPaths() {
        RateLimitWebFilter filter = new RateLimitWebFilter(rateLimitService);
        
        String[] authPaths = {"/auth/login", "/auth/register", "/auth/validate-otp"};
        
        for (String path : authPaths) {
            MockServerHttpRequest request = MockServerHttpRequest.get(path)
                    .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(rateLimitService.isAllowed(anyString(), anyInt(), any(Duration.class))).thenReturn(Mono.just(true));
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            filter.filter(exchange, chain).block();
        }
    }

    @Test
    void filter_usesRemoteAddress_whenNoXForwardedFor() {
        RateLimitWebFilter filter = new RateLimitWebFilter(rateLimitService);
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/validate")
                .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(rateLimitService.isAllowed(eq("192.168.1.1"), anyInt(), any(Duration.class))).thenReturn(Mono.just(true));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();
    }
}