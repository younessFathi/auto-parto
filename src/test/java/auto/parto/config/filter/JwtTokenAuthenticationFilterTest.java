package auto.parto.config.filter;

import auto.parto.config.JwtTokenAuthenticationFilter;
import auto.parto.config.ApiErrorResponseWriter;
import auto.parto.utils.JwtTokenGenerator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.WebFilterChain;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Date;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenAuthenticationFilterTest {

    @Mock
    private JwtTokenGenerator jwtTokenGenerator;

    @Mock
    private ApiErrorResponseWriter errorResponseWriter;

    @BeforeEach
    void stubErrorWriter() {
        lenient().when(errorResponseWriter.write(any(), any(), anyString(), anyString())).thenReturn(Mono.empty());
        filter = new JwtTokenAuthenticationFilter(jwtTokenGenerator, errorResponseWriter);
    }

    @Mock
    private WebFilterChain chain;

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
    void allowsHealthEndpointWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
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
    void allowsPublicHealthEndpointWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(errorResponseWriter, never()).write(any(), any(), anyString(), anyString());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void allowsMetricsWithoutJwtOnlyOnConfiguredPrivateManagementPort() {
        JwtTokenAuthenticationFilter managementFilter = new JwtTokenAuthenticationFilter(
                jwtTokenGenerator, errorResponseWriter, 8081);
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/prometheus")
                .localAddress(new InetSocketAddress("127.0.0.1", 8081)).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        managementFilter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verify(errorResponseWriter, never()).write(any(), any(), anyString(), anyString());
    }

    @Test
    void doesNotAllowMetricsWithoutJwtOnPublicPort() {
        JwtTokenAuthenticationFilter managementFilter = new JwtTokenAuthenticationFilter(
                jwtTokenGenerator, errorResponseWriter, 8081);
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/prometheus")
                .localAddress(new InetSocketAddress("127.0.0.1", 8080)).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        managementFilter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        verify(errorResponseWriter).write(exchange, HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", "Authentication required");
    }

    @Test
    void returns401WhenNoAuthorizationHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pieces").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        verify(errorResponseWriter).write(exchange, HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", "Authentication required");
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
        verify(errorResponseWriter).write(exchange, HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED", "Authentication failed");
    }

    @Test
    void returns401WhenTokenIsExpired() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pieces")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtTokenGenerator.validateToken("expired-token")).thenReturn(false);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        verify(errorResponseWriter).write(exchange, HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED", "Authentication failed");
    }

    @Test
    void returns401WhenSignedTokenHasNoCanonicalRole() {
        JwtTokenGenerator realGenerator = new JwtTokenGenerator();
        String secret = "H1lq8Mx5WjD3+KPxBlj+F+Qo5rFkNc1uWy2FpiGpWXM=";
        ReflectionTestUtils.setField(realGenerator, "secretKey", secret);
        ReflectionTestUtils.setField(realGenerator, "expirationTime", 3600000L);
        Date now = new Date();
        String token = Jwts.builder()
                .subject("buyer-123")
                .claim("principal_id", "buyer-123")
                .claim("email", "buyer@test.com")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000L))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)))
                .compact();
        JwtTokenAuthenticationFilter realFilter = new JwtTokenAuthenticationFilter(realGenerator, errorResponseWriter);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pieces")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        realFilter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        verify(errorResponseWriter).write(exchange, HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED", "Authentication failed");
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
