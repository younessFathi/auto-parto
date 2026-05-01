package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidationTest {

    @Test
    void skipsValidationWhenProdProfileIsNotActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        ProductionConfigValidation validation = new ProductionConfigValidation(environment);

        assertThatCode(validation::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void failsFastWhenProdSecretsAreMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ProductionConfigValidation validation = new ProductionConfigValidation(environment);

        assertThatThrownBy(validation::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.security.oauth2.client.registration.google.client-id")
                .hasMessageContaining("jwt.secretKey")
                .hasMessageContaining("resend.api-key")
                .hasMessageContaining("spring.data.mongodb.uri")
                .hasMessageContaining("spring.data.redis.password");
    }

    @Test
    void passesWhenAllRequiredProdPropertiesAreProvided() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.security.oauth2.client.registration.google.client-id", "client-id");
        environment.setProperty("spring.security.oauth2.client.registration.google.client-secret", "client-secret");
        environment.setProperty("jwt.secretKey", "jwt-secret");
        environment.setProperty("resend.api-key", "resend");
        environment.setProperty("spring.data.mongodb.uri", "mongodb://mongo:27017/auto-parto");
        environment.setProperty("spring.data.redis.password", "redis-secret");

        ProductionConfigValidation validation = new ProductionConfigValidation(environment);
        assertThatCode(validation::afterPropertiesSet).doesNotThrowAnyException();
    }
}
