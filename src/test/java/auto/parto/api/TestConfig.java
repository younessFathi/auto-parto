package auto.parto.api;

import auto.parto.service.RateLimitService;
import auto.parto.service.notification.PushNotificationQueue;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.mockito.Mockito.mock;

@TestConfiguration
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
}