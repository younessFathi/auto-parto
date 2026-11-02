package auto.parto.config;

import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisPoolSaturationIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void reactiveRedisConnectionsRespectPoolMaximumAndAcquireTimeout() {
        RedisStandaloneConfiguration redis = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        GenericObjectPoolConfig<StatefulConnection<?, ?>> pool = new GenericObjectPoolConfig<>();
        pool.setMaxTotal(1);
        pool.setMaxIdle(1);
        pool.setMinIdle(0);
        pool.setMaxWait(Duration.ofMillis(150));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis,
                LettucePoolingClientConfiguration.builder()
                        .poolConfig(pool)
                        .commandTimeout(Duration.ofSeconds(2))
                        .build());
        factory.setShareNativeConnection(false);
        factory.afterPropertiesSet();

        ReactiveRedisConnection held = factory.getReactiveConnection();
        ReactiveRedisConnection contender = factory.getReactiveConnection();
        try {
            assertThat(held.ping().block(Duration.ofSeconds(2))).isEqualTo("PONG");
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> contender.ping().block(Duration.ofSeconds(2)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
        } finally {
            contender.close();
            held.close();
            factory.destroy();
        }
    }
}
