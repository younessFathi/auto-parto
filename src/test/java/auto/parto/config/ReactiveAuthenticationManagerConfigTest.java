package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveAuthenticationManagerConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MinimalSecurityConfig.class);

    @Test
    void sourceFileWasDeleted() {
        assertThat(Path.of("src/main/java/auto/parto/config/ReactiveAuthenticationManagerConfig.java"))
                .doesNotExist();
    }

    @Test
    void contextLoadsWithoutBackdoorUsers() {
        runner
                .withPropertyValues(
                        "jwt.secretKey=RW5jb2RlZFNlY3JldEtleUZvclRlc3RpbmdQdXJwb3Nlcw==",
                        "jwt.expiration=3600000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("reactiveAuthenticationManager");
                    assertThat(context).doesNotHaveBean("userDetailsService");
                });
    }

    @Configuration
    static class MinimalSecurityConfig {

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        public auto.parto.utils.JwtTokenGenerator jwtTokenGenerator() {
            return new auto.parto.utils.JwtTokenGenerator();
        }
    }
}
