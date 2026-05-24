package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseConfig.class);

    @Test
    void contextFailsWhenEnabledButCredentialsMissing() {
        runner
                .withPropertyValues(
                        "firebase.enabled=true",
                        "firebase.credentials.json=",
                        "firebase.credentials.path="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("Firebase credentials not configured");
                });
    }

    @Test
    void contextFailsWhenEnabledAndCredentialsAreNotValidJson() {
        runner
                .withPropertyValues(
                        "firebase.enabled=true",
                        "firebase.credentials.json=not-a-json"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void contextSucceedsWhenFirebaseDisabled() {
        runner
                .withPropertyValues("firebase.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                });
    }
}
