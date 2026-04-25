package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorrelationIdWebFilterTest {

    @Test
    void addsGeneratedRequestIdWhenHeaderIsMissing() {
        CorrelationIdWebFilter filter = new CorrelationIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/pieces/customer-1").build()
        );
        WebFilterChain chain = webExchange -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String requestId = exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertFalse(requestId.isBlank());
        assertEquals(requestId, exchange.getAttribute(CorrelationIdWebFilter.REQUEST_ID_ATTRIBUTE));
    }

    @Test
    void keepsIncomingRequestIdWhenHeaderIsProvided() {
        CorrelationIdWebFilter filter = new CorrelationIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/pieces/customer-1")
                        .header(CorrelationIdWebFilter.REQUEST_ID_HEADER, "req-123")
                        .build()
        );
        WebFilterChain chain = webExchange -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("req-123", exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.REQUEST_ID_HEADER));
        assertEquals("req-123", exchange.getAttribute(CorrelationIdWebFilter.REQUEST_ID_ATTRIBUTE));
    }
}
