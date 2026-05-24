package auto.parto.utils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenGeneratorIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtTestConfig.class);

    @Test
    void contextFailsWhenJwtSecretIsEmpty() {
        runner
                .withPropertyValues(
                        "jwt.secretKey=",
                        "jwt.expiration=3600000"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("JWT secret key is not configured");
                });
    }

    @Test
    @Disabled
    void contextFailsWhenJwtSecretIsUnresolvedPlaceholder() {
        runner
                .withPropertyValues(
                        "jwt.secretKey=${JWT_SECRET}",
                        "jwt.expiration=3600000"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("JWT secret key is not configured");
                });
    }

    @Test
    void contextFailsWhenJwtSecretIsTooShort() {
        String shortKey = java.util.Base64.getEncoder().encodeToString(new byte[16]);
        runner
                .withPropertyValues(
                        "jwt.secretKey=" + shortKey,
                        "jwt.expiration=3600000"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("at least 256 bits");
                });
    }

    @Test
    void contextLoadsWhenJwtSecretIsValid() {
        runner
                .withPropertyValues(
                        "jwt.secretKey=RW5jb2RlZFNlY3JldEtleUZvclRlc3RpbmdQdXJwb3Nlcw==",
                        "jwt.expiration=3600000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtTokenGenerator.class);
                });
    }

    @Configuration
    static class JwtTestConfig {

        @Bean
        public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
            configurer.setIgnoreUnresolvablePlaceholders(true);
            return configurer;
        }

        @Bean
        public JwtTokenGenerator jwtTokenGenerator() {
            return new JwtTokenGenerator();
        }
    }
}
