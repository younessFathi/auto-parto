package auto.parto.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Test
    void isAllowed_returnsTrue_whenFirstRequest() {
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate:ip:192.168.1.1")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        RateLimitService service = new RateLimitService(redisTemplate);
        
        StepVerifier.create(service.isAllowed("192.168.1.1", 5, Duration.ofMinutes(1)))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isAllowed_returnsTrue_whenUnderLimit() {
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate:ip:192.168.1.1")).thenReturn(Mono.just(3L));

        RateLimitService service = new RateLimitService(redisTemplate);
        
        StepVerifier.create(service.isAllowed("192.168.1.1", 5, Duration.ofMinutes(1)))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isAllowed_returnsFalse_whenOverLimit() {
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate:ip:192.168.1.1")).thenReturn(Mono.just(6L));

        RateLimitService service = new RateLimitService(redisTemplate);
        
        StepVerifier.create(service.isAllowed("192.168.1.1", 5, Duration.ofMinutes(1)))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isAllowed_returnsTrue_onRedisError() {
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate:ip:192.168.1.1")).thenReturn(Mono.error(new RuntimeException("Redis error")));

        RateLimitService service = new RateLimitService(redisTemplate);
        
        StepVerifier.create(service.isAllowed("192.168.1.1", 5, Duration.ofMinutes(1)))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isAllowed_usesDefaultValues() {
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate:ip:192.168.1.1")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        RateLimitService service = new RateLimitService(redisTemplate);
        
        StepVerifier.create(service.isAllowed("192.168.1.1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void getRemainingRequests_returnsMax_whenNoKey() {
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rate:ip:192.168.1.1")).thenReturn(Mono.empty());

        RateLimitService service = new RateLimitService(redisTemplate);
        
        StepVerifier.create(service.getRemainingRequests("192.168.1.1", 5))
                .expectNext(5L)
                .verifyComplete();
    }

    @Test
    void getRemainingRequests_calculatesCorrectly() {
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rate:ip:192.168.1.1")).thenReturn(Mono.just("3"));

        RateLimitService service = new RateLimitService(redisTemplate);
        
        StepVerifier.create(service.getRemainingRequests("192.168.1.1", 5))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void getRemainingRequests_returnsZero_whenAtLimit() {
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rate:ip:192.168.1.1")).thenReturn(Mono.just("5"));

        RateLimitService service = new RateLimitService(redisTemplate);
        
        StepVerifier.create(service.getRemainingRequests("192.168.1.1", 5))
                .expectNext(0L)
                .verifyComplete();
    }
}