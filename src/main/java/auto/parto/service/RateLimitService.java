package auto.parto.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
public class RateLimitService {

    private final ObjectProvider<ReactiveRedisTemplate<String, String>> redisTemplateProvider;

    private static final String RATE_LIMIT_PREFIX = "rate:ip:";
    private static final int DEFAULT_MAX_REQUESTS = 5;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    public RateLimitService(ObjectProvider<ReactiveRedisTemplate<String, String>> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public Mono<Boolean> isAllowed(String ip, int maxRequests, Duration window) {
        ReactiveRedisTemplate<String, String> redisTemplate = redisTemplateProvider.getIfAvailable();
        
        if (redisTemplate == null) {
            log.warn("Redis not available, allowing request");
            return Mono.just(true);
        }

        String key = RATE_LIMIT_PREFIX + ip;

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, window).thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .map(count -> count <= maxRequests)
                .onErrorResume(e -> {
                    log.error("Rate limit check failed for IP: {}", ip, e);
                    return Mono.just(true);
                });
    }

    public Mono<Boolean> isAllowed(String ip) {
        return isAllowed(ip, DEFAULT_MAX_REQUESTS, DEFAULT_WINDOW);
    }

    public Mono<Long> getRemainingRequests(String ip, int maxRequests) {
        ReactiveRedisTemplate<String, String> redisTemplate = redisTemplateProvider.getIfAvailable();
        
        if (redisTemplate == null) {
            return Mono.just((long) maxRequests);
        }

        String key = RATE_LIMIT_PREFIX + ip;

        return redisTemplate.opsForValue()
                .get(key)
                .map(countStr -> {
                    long count = Long.parseLong(countStr);
                    return Math.max(0, maxRequests - count);
                })
                .defaultIfEmpty((long) maxRequests);
    }
}