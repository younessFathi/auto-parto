package auto.parto.service.notification;

import auto.parto.service.BasicCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(NotificationSchedulingTest.Config.class)
@TestPropertySource(properties = {
        "notification.delivery.recovery-delay-ms=25",
        "notification.timeout=30"
})
class NotificationSchedulingTest {

    @org.springframework.beans.factory.annotation.Autowired
    private HybridNotificationStore notificationStore;

    @Test
    void scheduledRecoveryInvokesDueWork() {
        when(notificationStore.getPendingStats()).thenReturn(Mono.just(Map.of("totalNotifications", 0L)));

        verify(notificationStore, timeout(2_000).atLeastOnce()).getPendingStats();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class Config {
        @Bean
        SSENotificationService sseNotificationService() {
            return new SSENotificationService();
        }

        @Bean
        HybridNotificationStore notificationStore() {
            HybridNotificationStore store = mock(HybridNotificationStore.class);
            when(store.getPendingStats()).thenReturn(Mono.just(Map.of("totalNotifications", 0L)));
            return store;
        }

        @Bean
        BasicCircuitBreaker circuitBreaker() {
            return mock(BasicCircuitBreaker.class);
        }

        @Bean
        ResilientPendingNotificationService resilientPendingNotificationService(
                HybridNotificationStore store, SSENotificationService sse,
                BasicCircuitBreaker breaker) {
            return new ResilientPendingNotificationService(store, sse, breaker);
        }
    }
}
