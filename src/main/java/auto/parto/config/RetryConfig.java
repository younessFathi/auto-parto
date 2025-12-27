package auto.parto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {

    @Value("${firebase.messaging.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${firebase.messaging.retry.backoff-delay:1000}")
    private long initialInterval;

    @Bean
    public RetryTemplate fcmRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Exponential backoff configuration
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval);
        backOffPolicy.setMultiplier(2.0);
        // Set a cap to prevent waiting too long (e.g., 10 seconds)
        backOffPolicy.setMaxInterval(10000);

        // Retry Policy
        // Note: SimpleRetryPolicy counts the initial attempt.
        // maxAttempts = 3 means: 1 Initial Attempt + 2 Retries.
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }
}
