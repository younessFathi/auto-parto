package auto.parto.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorrelationIdWebFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("correlation-test");
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void detachLogAppender() {
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
        MDC.clear();
    }

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

    @Test
    void propagatesRequestIdToStructuredLogContextAcrossSchedulerBoundary() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        CorrelationIdWebFilter filter = new CorrelationIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/pieces/customer-1")
                        .header(CorrelationIdWebFilter.REQUEST_ID_HEADER, "req-async-456")
                        .build()
        );
        Scheduler scheduler = Schedulers.newSingle("correlation-test");
        WebFilterChain chain = webExchange -> Mono.just("log")
                .publishOn(scheduler)
                .doOnNext(message -> logger.info(message))
                .then();

        try {
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            List<ILoggingEvent> matchingEvents = appender.list.stream()
                    .filter(event -> event.getFormattedMessage().equals("log"))
                    .toList();
            assertThat(matchingEvents).hasSize(1);
            assertThat(matchingEvents.getFirst().getMDCPropertyMap())
                    .containsEntry(CorrelationIdWebFilter.MDC_KEY, "req-async-456");
            assertThat(MDC.get(CorrelationIdWebFilter.MDC_KEY)).isNull();
        } finally {
            scheduler.dispose();
        }
    }
}
