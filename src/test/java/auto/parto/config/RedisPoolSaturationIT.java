package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
class RedisPoolSaturationIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void configuredFactoryAppliesSeparateSyncAndReactiveBudgetsAndReleasesSaturatedConnections() {
        new ApplicationContextRunner()
                .withUserConfiguration(RedisConfig.class)
                .withPropertyValues(
                        "spring.data.redis.host=" + REDIS.getHost(),
                        "spring.data.redis.port=" + REDIS.getMappedPort(6379),
                        "spring.data.redis.lettuce.pool.max-active=1",
                        "spring.data.redis.lettuce.pool.max-idle=1",
                        "spring.data.redis.lettuce.pool.min-idle=0",
                        "spring.data.redis.lettuce.pool.max-wait=250ms",
                        "spring.data.redis.timeout=2s",
                        "spring.data.redis.connect-timeout=1s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    RedisConnectionFactory synchronousFactory = context.getBean(RedisConnectionFactory.class);
                    ReactiveRedisConnectionFactory reactiveFactory = context.getBean(ReactiveRedisConnectionFactory.class);
                    assertThat(synchronousFactory).isSameAs(factory);
                    assertThat(reactiveFactory).isSameAs(factory);

                    RedisConnection synchronous = synchronousFactory.getConnection();
                    ReactiveRedisConnection reactive = reactiveFactory.getReactiveConnection();
                    ReactiveRedisConnection reactiveContender = null;
                    try {
                        assertThat(synchronous.ping()).isEqualTo("PONG");
                        assertThat(reactive.ping().block(Duration.ofSeconds(2))).isEqualTo("PONG");

                        long synchronousWaitStarted = System.nanoTime();
                        RedisConnection synchronousContender = synchronousFactory.getConnection();
                        Throwable synchronousSaturation;
                        try {
                            synchronousSaturation = catchThrowable(synchronousContender::ping);
                        } finally {
                            synchronousContender.close();
                        }
                        Duration synchronousWait = Duration.ofNanos(System.nanoTime() - synchronousWaitStarted);
                        assertPoolFailure(synchronousSaturation, "Timeout waiting for idle object");
                        assertThat(synchronousWait).isGreaterThanOrEqualTo(Duration.ofMillis(150))
                                .isLessThan(Duration.ofSeconds(2));

                        reactiveContender = reactiveFactory.getReactiveConnection();
                        ReactiveRedisConnection saturatedReactive = reactiveContender;
                        long reactiveWaitStarted = System.nanoTime();
                        Throwable reactiveSaturation = catchThrowable(
                                () -> saturatedReactive.ping().block(Duration.ofSeconds(1)));
                        Duration reactiveWait = Duration.ofNanos(System.nanoTime() - reactiveWaitStarted);
                        assertPoolFailure(reactiveSaturation, "Pool exhausted");
                        assertThat(reactiveWait).isLessThan(Duration.ofSeconds(1));
                        reactiveContender.closeLater().block(Duration.ofSeconds(1));
                        reactiveContender = null;

                        reactive.closeLater().block(Duration.ofSeconds(1));
                        reactive = reactiveFactory.getReactiveConnection();
                        assertThat(reactive.ping().block(Duration.ofSeconds(2))).isEqualTo("PONG");
                    } finally {
                        if (reactiveContender != null) {
                            reactiveContender.closeLater().block(Duration.ofSeconds(1));
                        }
                        reactive.closeLater().block(Duration.ofSeconds(1));
                        synchronous.close();
                    }
                });
    }

    private static void assertPoolFailure(Throwable failure, String expectedMessage) {
        assertThat(findPoolFailure(failure, expectedMessage))
                .as("expected pool exhaustion with diagnostic '%s', actual failure was %s", expectedMessage, failure)
                .isNotNull();
    }

    private static Throwable findPoolFailure(Throwable failure, String expectedMessage) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoSuchElementException
                    && current.getMessage() != null
                    && current.getMessage().contains(expectedMessage)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }
}
