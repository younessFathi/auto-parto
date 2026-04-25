package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidationTest {

    @Test
    void skipsValidationWhenProfileIsNotProd() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        ProductionConfigValidation validator = new ProductionConfigValidation(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void failsFastWhenProdSecretsAreMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ProductionConfigValidation validator = new ProductionConfigValidation(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.security.oauth2.client.registration.google.client-id")
                .hasMessageContaining("jwt.secretKey")
                .hasMessageContaining("resend.api-key");
    }

    @Test
    void passesWhenRequiredProdPropertiesAreSet() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.security.oauth2.client.registration.google.client-id", "id");
        environment.setProperty("spring.security.oauth2.client.registration.google.client-secret", "secret");
        environment.setProperty("jwt.secretKey", "jwt-secret");
        environment.setProperty("twilio.sid", "sid");
        environment.setProperty("twilio.auth", "auth");
        environment.setProperty("resend.api-key", "resend");
        environment.setProperty("spring.data.mongodb.uri", "mongodb://user:pass@mongo:27017/db");
        environment.setProperty("spring.data.redis.password", "redis-pass");
        ProductionConfigValidation validator = new ProductionConfigValidation(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }
}
