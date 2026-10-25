package auto.parto.config;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class FirebaseHealthIndicatorTest {

    @Test
    void doesNotExposeProviderExceptionDetails() {
        try (MockedStatic<FirebaseApp> firebaseApp = mockStatic(FirebaseApp.class)) {
            firebaseApp.when(FirebaseApp::getInstance)
                    .thenThrow(new IllegalStateException("sensitive provider configuration"));

            Health health = new FirebaseHealthIndicator().health().block();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("firebase", "DISCONNECTED")
                    .doesNotContainKey("error");
        }
    }
}
