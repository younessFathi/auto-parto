package auto.parto.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CircuitBreakerAutoPartoConfig {

    @Value("${firebase.messaging.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${firebase.messaging.circuit-breaker.reset-timeout:30000}")
    private long resetTimeout;

    @Bean
    public CircuitBreaker fcmCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Open if 50% of requests fail
                .slidingWindowSize(failureThreshold)
                .minimumNumberOfCalls(failureThreshold)
                .waitDurationInOpenState(Duration.ofMillis(resetTimeout))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        return registry.circuitBreaker("fcm", config);
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

}
