package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidationTest {

    @Test
    void skipsValidationOutsideProd() {
        MockEnvironment environment = new MockEnvironment().withProperty("jwt.secretKey", "");
        environment.setActiveProfiles("test");
        assertThatCode(() -> new ProductionConfigValidation(environment).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void failsClosedWhenRequiredProductionConfigurationIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.r2dbc.url")
                .hasMessageContaining("spring.data.redis.password")
                .hasMessageContaining("jwt.signing.private-key")
                .hasMessageContaining("auto.parto.storage.provider");
    }

    @Test
    void rejectsDevelopmentFallbacksAndInvalidSecretMaterial() {
        MockEnvironment environment = validProductionEnvironment();
        environment.setProperty("spring.r2dbc.url", "r2dbc:postgresql://localhost:5432/app");
        environment.setProperty("spring.data.redis.port", "70000");
        environment.setProperty("jwt.signing.algorithm", "HS256");
        environment.setProperty("auto.parto.storage.s3.endpoint", "http://storage.internal");

        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.r2dbc.url must not use a loopback host")
                .hasMessageContaining("spring.data.redis.port must be between 1 and 65535")
                .hasMessageContaining("jwt.signing.algorithm=RS256")
                .hasMessageContaining("auto.parto.storage.s3.endpoint must use HTTPS");
    }

    @Test
    void rejectsIpv4LoopbackInfrastructureEndpoints() {
        assertRejectsLoopback("spring.r2dbc.url", "r2dbc:postgresql://127.0.0.1:5432/app", "spring.r2dbc.url");
        assertRejectsLoopback("spring.data.redis.host", "127.0.0.1", "spring.data.redis.host");
        assertRejectsLoopback("auto.parto.storage.s3.endpoint", "https://127.0.0.1", "auto.parto.storage.s3.endpoint");
    }

    @Test
    void rejectsIpv6LoopbackInfrastructureEndpoints() {
        assertRejectsLoopback("spring.r2dbc.url", "r2dbc:postgresql://[::1]:5432/app", "spring.r2dbc.url");
        assertRejectsLoopback("spring.data.redis.host", "[::1]", "spring.data.redis.host");
        assertRejectsLoopback("auto.parto.storage.s3.endpoint", "https://[::1]", "auto.parto.storage.s3.endpoint");
    }

    @Test
    void rejectsDatabaseCredentialsEmbeddedInProductionUrl() {
        MockEnvironment environment = validProductionEnvironment()
                .withProperty("spring.r2dbc.url", "r2dbc:postgresql://runtime:secret@postgres.internal:5432/app");

        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.r2dbc.url must not contain embedded credentials");
    }

    @Test
    void rejectsPostgresqlAndRedisConnectionsWithoutVerifiedTls() {
        MockEnvironment environment = validProductionEnvironment()
                .withProperty("spring.r2dbc.url", "r2dbc:postgresql://postgres.internal:5432/app")
                .withProperty("spring.data.redis.ssl.enabled", "false");

        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.r2dbc.url must require sslMode=VERIFY_FULL")
                .hasMessageContaining("spring.data.redis.ssl.enabled=true");
    }

    @Test
    void rejectsUnboundedOrOversizedDatabaseAndRedisPools() {
        MockEnvironment environment = validProductionEnvironment()
                .withProperty("spring.r2dbc.pool.max-size", "21")
                .withProperty("spring.r2dbc.pool.max-acquire-time", "-1s")
                .withProperty("spring.data.redis.lettuce.pool.max-active", "-1")
                .withProperty("spring.data.redis.lettuce.pool.max-wait", "30s");

        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.r2dbc.pool.max-size must be between 1 and 20")
                .hasMessageContaining("spring.r2dbc.pool.max-acquire-time must be a finite positive timeout")
                .hasMessageContaining("spring.data.redis.lettuce.pool.max-active must be between 1 and 32")
                .hasMessageContaining("spring.data.redis.lettuce.pool.max-wait must be a finite positive timeout");
    }

    @Test
    void rejectsAProductionProfileThatLeavesMongoOrCutoverRuntimeEnabled() {
        MockEnvironment environment = validProductionEnvironment();
        environment.setProperty("auto.parto.persistence.postgresql.cutover.mongo-disabled", "false");
        environment.setProperty("spring.autoconfigure.exclude", "");

        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auto.parto.persistence.postgresql.cutover.mongo-disabled=true")
                .hasMessageContaining("spring.autoconfigure.exclude must disable Mongo reactive runtime");
    }

    @Test
    void requiresEnabledEmailFirebaseAndAuditCredentials() {
        MockEnvironment environment = validProductionEnvironment();
        environment.setProperty("resend.enable", "true");
        environment.setProperty("firebase.enabled", "true");
        environment.setProperty("auto.parto.audit.enabled", "true");

        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resend.api-key")
                .hasMessageContaining("firebase.credentials.json or firebase.credentials.path")
                .hasMessageContaining("auto.parto.audit.r2dbc-url")
                .hasMessageContaining("auto.parto.audit.username")
                .hasMessageContaining("auto.parto.audit.password");
    }

    @Test
    void passesWithProtectedDefaultsAndDisabledOptionalProviders() {
        assertThatCode(() -> new ProductionConfigValidation(validProductionEnvironment()).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsEnabledProvidersWhenTheirCredentialsAreConfigured() {
        MockEnvironment environment = validProductionEnvironment()
                .withProperty("resend.enable", "true")
                .withProperty("resend.api-key", "provider-key")
                .withProperty("firebase.enabled", "true")
                .withProperty("firebase.credentials.json", "credential-document")
                .withProperty("auto.parto.audit.enabled", "true")
                .withProperty("auto.parto.audit.r2dbc-url", "r2dbc:postgresql://audit.internal:5432/audit?sslMode=VERIFY_FULL")
                .withProperty("auto.parto.audit.username", "audit-runtime")
                .withProperty("auto.parto.audit.password", "audit-password");

        assertThatCode(() -> new ProductionConfigValidation(environment).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void rejectsEnabledAuditDatabaseWithoutVerifiedTls() {
        MockEnvironment environment = validProductionEnvironment()
                .withProperty("auto.parto.audit.enabled", "true")
                .withProperty("auto.parto.audit.r2dbc-url", "r2dbc:postgresql://audit.internal:5432/audit")
                .withProperty("auto.parto.audit.username", "audit-runtime")
                .withProperty("auto.parto.audit.password", "audit-password");

        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auto.parto.audit.r2dbc-url must require sslMode=VERIFY_FULL");
    }

    private static MockEnvironment validProductionEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.r2dbc.url", "r2dbc:postgresql://postgres.internal:5432/app?sslMode=VERIFY_FULL")
                .withProperty("spring.r2dbc.username", "runtime")
                .withProperty("spring.r2dbc.password", "runtime-password")
                .withProperty("spring.data.redis.host", "redis.internal")
                .withProperty("spring.data.redis.port", "6379")
                .withProperty("spring.data.redis.password", "redis-password")
                .withProperty("spring.data.redis.ssl.enabled", "true")
                .withProperty("spring.data.redis.lettuce.pool.max-active", "8")
                .withProperty("spring.data.redis.lettuce.pool.max-idle", "8")
                .withProperty("spring.data.redis.lettuce.pool.min-idle", "0")
                .withProperty("spring.data.redis.lettuce.pool.max-wait", "3s")
                .withProperty("spring.data.redis.connect-timeout", "3s")
                .withProperty("spring.data.redis.timeout", "3s")
                .withProperty("spring.r2dbc.pool.max-size", "10")
                .withProperty("spring.r2dbc.pool.max-acquire-time", "5s")
                .withProperty("spring.r2dbc.pool.max-create-connection-time", "5s")
                .withProperty("jwt.signing.algorithm", "RS256")
                .withProperty("jwt.signing.key-id", "active")
                .withProperty("jwt.signing.private-key", "test-key-material")
                .withProperty("jwt.signing.verification-keys", "{\"active\":\"test-key\"}")
                .withProperty("auto.parto.storage.provider", "s3")
                .withProperty("auto.parto.storage.s3.endpoint", "https://storage.internal")
                .withProperty("auto.parto.storage.s3.bucket", "auto-parto")
                .withProperty("auto.parto.storage.s3.access-key", "access-key")
                .withProperty("auto.parto.storage.s3.secret-key", "storage-secret")
                .withProperty("resend.enable", "false")
                .withProperty("firebase.enabled", "false")
                .withProperty("auto.parto.audit.enabled", "false")
                .withProperty("auto.parto.persistence.postgresql.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.identity.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.buyer-identity.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.marketplace.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.wallet.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.device-tokens.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.notifications.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.files.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.notification-preferences.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.cutover.enabled", "true")
                .withProperty("auto.parto.persistence.postgresql.cutover.api-contracts-ready", "true")
                .withProperty("auto.parto.persistence.postgresql.cutover.mongo-disabled", "true")
                .withProperty("auto.parto.persistence.postgresql.cutover.storage-reconciliation.enabled", "true")
                .withProperty("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration")
                .withProperty("spring.data.redis.database", "0")
                .withProperty("spring.data.redis.timeout", "2000")
                .withProperty("spring.r2dbc.pool.enabled", "true")
                .withProperty("spring.r2dbc.pool.max-size", "10")
                .withProperty("spring.r2dbc.pool.max-idle-time", "30m")
                .withProperty("spring.r2dbc.pool.initial-size", "0")
                .withProperty("spring.r2dbc.pool.max-create-connection-time", "10s")
                .withProperty("spring.r2dbc.pool.max-acquire-time", "10s")
                .withProperty("spring.r2dbc.pool.max-life-time", "30m")
                .withProperty("spring.r2dbc.pool.validation-query", "SELECT 1");
        environment.setActiveProfiles("prod");
        return environment;
    }

    private static void assertRejectsLoopback(String property, String value, String expectedMessage) {
        MockEnvironment environment = validProductionEnvironment().withProperty(property, value);
        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage)
                .hasMessageContaining("loopback");
    }
}
