package auto.parto.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials.path}")
    private Resource firebaseCredentialsPath;

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        FirebaseApp firebaseApp;
        List<FirebaseApp> apps = FirebaseApp.getApps();

        // Avoid initializing the app multiple times (common issue during hot reloads)
        if (apps.isEmpty()) {
            try (InputStream inputStream = firebaseCredentialsPath.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(inputStream))
                        .build();
                firebaseApp = FirebaseApp.initializeApp(options);
            }
        } else {
            firebaseApp = apps.getFirst();
        }

        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
