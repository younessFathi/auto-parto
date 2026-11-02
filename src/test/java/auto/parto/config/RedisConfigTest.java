package auto.parto.config;

import io.lettuce.core.SslVerifyMode;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.KeyManagerFactory;
import java.security.KeyStore;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisConfigTest {

    @Test
    void createsTlsVerifiedPooledFactoryWithConfiguredAcquisitionBound() throws Exception {
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init((KeyStore) null);
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init((KeyStore) null, new char[0]);
        SslBundle redisBundle = mock(SslBundle.class);
        when(redisBundle.getManagers()).thenReturn(SslManagerBundle.of(keyManagers, trustManagers));
        SslBundles bundles = mock(SslBundles.class);
        when(bundles.getBundle("redis")).thenReturn(redisBundle);

        new ApplicationContextRunner()
                .withUserConfiguration(RedisConfig.class)
                .withBean(SslBundles.class, () -> bundles)
                .withPropertyValues(
                        "spring.data.redis.host=redis.internal",
                        "spring.data.redis.ssl.enabled=true",
                        "spring.data.redis.ssl.bundle=redis",
                        "spring.data.redis.lettuce.pool.max-active=2",
                        "spring.data.redis.lettuce.pool.max-idle=1",
                        "spring.data.redis.lettuce.pool.min-idle=0",
                        "spring.data.redis.lettuce.pool.max-wait=125ms",
                        "spring.data.redis.timeout=2s",
                        "spring.data.redis.connect-timeout=1s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getClientConfiguration()).isInstanceOf(LettucePoolingClientConfiguration.class);
                    LettucePoolingClientConfiguration clientConfiguration =
                            (LettucePoolingClientConfiguration) factory.getClientConfiguration();
                    GenericObjectPoolConfig<?> pool = clientConfiguration.getPoolConfig();
                    assertThat(pool.getMaxTotal()).isEqualTo(2);
                    assertThat(pool.getMaxWaitDuration()).isEqualTo(Duration.ofMillis(125));
                    assertThat(clientConfiguration.isUseSsl()).isTrue();
                    assertThat(clientConfiguration.getVerifyMode()).isEqualTo(SslVerifyMode.FULL);
                });
    }

    @Test
    void tlsEnabledFactoryFailsClosedWhenTrustedBundleIsMissing() {
        SslBundles bundles = mock(SslBundles.class);
        new ApplicationContextRunner()
                .withUserConfiguration(RedisConfig.class)
                .withBean(SslBundles.class, () -> bundles)
                .withPropertyValues(
                        "spring.data.redis.ssl.enabled=true",
                        "spring.data.redis.ssl.bundle=")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("spring.data.redis.ssl.bundle must name a trusted Redis TLS bundle"));
    }
}
