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
                .hasMessageContaining("auto.parto.secrets.provider")
                .hasMessageContaining("spring.r2dbc.url")
                .hasMessageContaining("spring.data.redis.password")
                .hasMessageContaining("jwt.secretKey")
                .hasMessageContaining("auto.parto.storage.provider");
    }

    @Test
    void rejectsDevelopmentFallbacksAndInvalidSecretMaterial() {
        MockEnvironment environment = validProductionEnvironment();
        environment.setProperty("spring.r2dbc.url", "r2dbc:postgresql://localhost:5432/app");
        environment.setProperty("spring.data.redis.port", "70000");
        environment.setProperty("jwt.secretKey", "too-short");
        environment.setProperty("auto.parto.storage.s3.endpoint", "http://storage.internal");

        assertThatThrownBy(() -> new ProductionConfigValidation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.r2dbc.url must be a protected PostgreSQL URL")
                .hasMessageContaining("spring.data.redis.port must be between 1 and 65535")
                .hasMessageContaining("jwt.secretKey must be Base64 encoded")
                .hasMessageContaining("auto.parto.storage.s3.endpoint must use HTTPS");
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
                .withProperty("auto.parto.audit.r2dbc-url", "r2dbc:postgresql://audit.internal:5432/audit")
                .withProperty("auto.parto.audit.username", "audit-runtime")
                .withProperty("auto.parto.audit.password", "audit-password");

        assertThatCode(() -> new ProductionConfigValidation(environment).afterPropertiesSet()).doesNotThrowAnyException();
    }

    private static MockEnvironment validProductionEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("auto.parto.secrets.provider", "openbao")
                .withProperty("auto.parto.secrets.openbao.address", "https://openbao.internal")
                .withProperty("auto.parto.secrets.openbao.role", "auto-parto")
                .withProperty("auto.parto.secrets.openbao.auth-method", "kubernetes")
                .withProperty("spring.r2dbc.url", "r2dbc:postgresql://postgres.internal:5432/app")
                .withProperty("spring.r2dbc.username", "runtime")
                .withProperty("spring.r2dbc.password", "runtime-password")
                .withProperty("spring.data.redis.host", "redis.internal")
                .withProperty("spring.data.redis.port", "6379")
                .withProperty("spring.data.redis.password", "redis-password")
                .withProperty("jwt.secretKey", "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
                .withProperty("auto.parto.storage.provider", "s3")
                .withProperty("auto.parto.storage.s3.endpoint", "https://storage.internal")
                .withProperty("auto.parto.storage.s3.bucket", "auto-parto")
                .withProperty("auto.parto.storage.s3.access-key", "access-key")
                .withProperty("auto.parto.storage.s3.secret-key", "storage-secret")
                .withProperty("resend.enable", "false")
                .withProperty("firebase.enabled", "false")
                .withProperty("auto.parto.audit.enabled", "false")
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
}
