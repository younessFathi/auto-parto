package auto.parto.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
import io.lettuce.core.SslVerifyMode;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:2s}")
    private String timeout;

    @Value("${spring.data.redis.connect-timeout:2s}")
    private String connectTimeout;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${spring.data.redis.ssl.bundle:}")
    private String sslBundleName;

    @Value("${spring.data.redis.lettuce.pool.max-active:8}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:8}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:0}")
    private int minIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:3s}")
    private String maxWait;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(ObjectProvider<SslBundles> sslBundlesProvider) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(redisDatabase);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        GenericObjectPoolConfig<StatefulConnection<?, ?>> pool = new GenericObjectPoolConfig<>();
        pool.setMaxTotal(maxActive);
        pool.setMaxIdle(maxIdle);
        pool.setMinIdle(minIdle);
        pool.setMaxWait(DurationStyle.detectAndParse(maxWait));

        ClientOptions.Builder clientOptions = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(DurationStyle.detectAndParse(connectTimeout)).build());

        if (sslEnabled) {
            if (sslBundleName.isBlank()) {
                throw new IllegalStateException("spring.data.redis.ssl.bundle must name a trusted Redis TLS bundle");
            }
            SslBundles sslBundles = sslBundlesProvider.getIfAvailable();
            if (sslBundles == null) {
                throw new IllegalStateException("Spring SSL bundles must be configured for the production Redis connection");
            }
            SslBundle bundle = sslBundles.getBundle(sslBundleName);
            clientOptions.sslOptions(SslOptions.builder()
                            .jdkSslProvider()
                            .trustManager(bundle.getManagers().getTrustManagerFactory())
                            .build());
        }

        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder clientConfig =
                LettucePoolingClientConfiguration.builder()
                        .poolConfig(pool)
                        .commandTimeout(DurationStyle.detectAndParse(timeout))
                        .clientOptions(clientOptions.build());
        if (sslEnabled) {
            clientConfig.useSsl().verifyPeer(SslVerifyMode.FULL).and();
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig.build());
        // Route reactive and synchronous operations through the bounded pool.
        factory.setShareNativeConnection(false);
        return factory;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10)))
                .build();
    }

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer();

        RedisSerializationContext.RedisSerializationContextBuilder<String, Object> builder =
                RedisSerializationContext.newSerializationContext(keySerializer);

        RedisSerializationContext<String, Object> context =
                builder.value(valueSerializer).build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    @Primary
    public ReactiveRedisTemplate<String, String> stringReactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        return new ReactiveRedisTemplate<>(
                factory,
                RedisSerializationContext.string()
        );
    }
}
