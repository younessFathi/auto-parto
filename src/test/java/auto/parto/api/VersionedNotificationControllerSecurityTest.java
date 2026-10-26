package auto.parto.api;

import auto.parto.service.notification.HybridNotificationStore;
import auto.parto.service.notification.ResilientNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(controllers = VersionedNotificationController.class)
@Import(TestConfig.class)
class VersionedNotificationControllerSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ResilientNotificationService connectionService;

    @MockitoBean
    private HybridNotificationStore notificationStore;

    @Test
    void streamRequiresAuthentication() {
        assertThat(webTestClient.get()
                .uri("/api/v1/notifications/stream")
                .exchange()
                .returnResult(String.class)
                .getStatus().value()).isEqualTo(401);
    }
}
