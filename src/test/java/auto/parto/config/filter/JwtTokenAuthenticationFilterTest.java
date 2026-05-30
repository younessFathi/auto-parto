package auto.parto.config.filter;

import auto.parto.config.JwtTokenAuthenticationFilter;
import auto.parto.utils.JwtTokenGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenAuthenticationFilterTest {

    @Mock
    private JwtTokenGenerator jwtTokenGenerator;

    @Mock
    private WebFilterChain chain;

    @InjectMocks
    private JwtTokenAuthenticationFilter filter;

    @Test
    void allowsPublicAuthEndpointWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/send-otp-email").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void allowsPublicSwaggerEndpointWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/swagger-ui/index.html").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void allowsPublicApiDocsEndpointWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/v3/api-docs").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void allowsPublicWebjarsEndpointWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/webjars/swagger-ui/index.html").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void returns401WhenNoAuthorizationHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pieces").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returns401WhenTokenIsInvalid() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pieces")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtTokenGenerator.validateToken("invalid-token")).thenReturn(false);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void proceedsWithAuthenticationWhenTokenIsValid() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pieces")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "valid-token", null);

        when(jwtTokenGenerator.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenGenerator.getAuthentication("valid-token")).thenReturn(auth);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
