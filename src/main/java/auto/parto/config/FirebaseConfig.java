package auto.parto.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials.json:}")
    private String firebaseCredentialsJson;

    @Value("${firebase.credentials.path:}")
    private Resource firebaseCredentialsPath;

    @Bean(destroyMethod = "delete")
    public FirebaseApp firebaseApp() throws IOException {
        FirebaseApp firebaseApp;
        List<FirebaseApp> apps = FirebaseApp.getApps();

        if (apps.isEmpty()) {
            InputStream inputStream = resolveCredentialsStream();
            if (inputStream == null) {
                throw new IllegalStateException(
                    "Firebase credentials not configured. Set FIREBASE_CREDENTIALS_JSON " +
                    "environment variable to the Firebase service account JSON, or disable " +
                    "Firebase by setting FIREBASE_ENABLED=false."
                );
            }
            try (inputStream) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(inputStream))
                        .build();
                firebaseApp = FirebaseApp.initializeApp(options);
            }
        } else {
            firebaseApp = apps.getFirst();
        }

        return firebaseApp;
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private InputStream resolveCredentialsStream() throws IOException {
        if (firebaseCredentialsJson != null && !firebaseCredentialsJson.isBlank()) {
            String raw = firebaseCredentialsJson.trim();
            if (raw.startsWith("{")) {
                return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
            }
            try {
                return new ByteArrayInputStream(Base64.getDecoder().decode(raw));
            } catch (IllegalArgumentException e) {
                log.warn("FIREBASE_CREDENTIALS_JSON is not valid JSON or Base64, falling back to file path");
            }
        }
        if (firebaseCredentialsPath != null && firebaseCredentialsPath.exists()) {
            return firebaseCredentialsPath.getInputStream();
        }
        return null;
    }
}
