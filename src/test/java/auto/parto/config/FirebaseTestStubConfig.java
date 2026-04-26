package auto.parto.config;

import com.google.firebase.messaging.FirebaseMessaging;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a no-op stub for FirebaseMessaging when firebase.enabled=false.
 * This allows the full Spring ApplicationContext to load in CI/test environments
 * without requiring the credentials file on the classpath.
 *
 * Beans dependent on FirebaseMessaging (e.g. ResilientFcmService) will be
 * wired with this stub and will simply be non-functional — which is acceptable
 * in a test context where FCM push notifications are not exercised.
 */
@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "false")
public class FirebaseTestStubConfig {

    @Bean
    @ConditionalOnMissingBean(FirebaseMessaging.class)
    public FirebaseMessaging firebaseMessaging() {
        return Mockito.mock(FirebaseMessaging.class);
    }
}

