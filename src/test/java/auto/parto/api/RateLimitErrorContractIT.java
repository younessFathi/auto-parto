package auto.parto.api;

import auto.parto.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = RateLimitErrorContractIT.RateLimitProbeController.class)
@Import({TestConfig.class, auto.parto.config.filter.RateLimitWebFilter.class})
class RateLimitErrorContractIT {

    @RestController
    static class RateLimitProbeController {
        @GetMapping("/auth/send-otp-email")
        Mono<Void> sendOtp() {
            return Mono.empty();
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RateLimitService rateLimitService;

    @Test
    void returnsDocumentedContractWhenAuthRateLimitIsExceeded() {
        when(rateLimitService.isAllowed(anyString(), anyInt(), any())).thenReturn(Mono.just(false));

        webTestClient.get()
                .uri("/auth/send-otp-email")
                .header("X-Request-Id", "rate-limit-001")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("X-Request-Id", "rate-limit-001")
                .expectBody()
                .jsonPath("$.status").isEqualTo(429)
                .jsonPath("$.code").isEqualTo("RATE_LIMITED")
                .jsonPath("$.message").isEqualTo("Too many requests")
                .jsonPath("$.requestId").isEqualTo("rate-limit-001")
                .jsonPath("$.path").isEqualTo("/auth/send-otp-email");
    }
}
