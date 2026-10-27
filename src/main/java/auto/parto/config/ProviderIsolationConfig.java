package auto.parto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ProviderIsolationConfig {

    @Bean(name = "resendProviderScheduler", destroyMethod = "close")
    public BoundedProviderScheduler resendProviderScheduler(
            @Value("${auto.parto.providers.email.threads:4}") int threads,
            @Value("${auto.parto.providers.email.queue-capacity:64}") int queueCapacity,
            @Value("${auto.parto.providers.email.shutdown-grace:10s}") Duration shutdownGrace) {
        return new BoundedProviderScheduler("resend-provider", threads, queueCapacity, shutdownGrace);
    }
}
