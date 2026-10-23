package auto.parto.api;

import auto.parto.dto.NotificationRequest;
import auto.parto.service.notification.PendingNotificationService;
import auto.parto.service.notification.ResilientNotificationService;
import auto.parto.service.notification.ResilientPendingNotificationService;
import auto.parto.service.notification.SSENotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

@WebFluxTest(controllers = NotificationController.class)
@Import(TestConfig.class)
class NotificationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ResilientNotificationService resilientNotificationService;

    @MockitoBean
    private PendingNotificationService pendingNotificationService;

    @MockitoBean
    private ResilientPendingNotificationService resilientPendingNotificationService;

    @MockitoBean
    private SSENotificationService sseNotificationService;

    @Test
    @WithMockUser
    void subscribe_returnsSseStream() {
        webTestClient.get()
                .uri("/notifications/subscribe")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void subscribeToUser_returnsSseStream() {
        Sinks.Many<NotificationRequest> userSink = Sinks.many().multicast().onBackpressureBuffer();
        when(resilientNotificationService.registerConnection("user-1")).thenReturn(userSink);
        when(resilientPendingNotificationService.deliverPendingOnConnect("user-1")).thenReturn(Mono.empty());

        Sinks.Many<NotificationRequest> connectionSink = Sinks.many().multicast().onBackpressureBuffer();
        connectionSink.tryEmitNext(new NotificationRequest("id-1", "user-1", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis()));
        connectionSink.tryEmitComplete();
        when(sseNotificationService.getUserConnections("user-1")).thenReturn(connectionSink);

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("user-1")))
                .get()
                .uri("/notifications/subscribe/user-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void subscribeToUser_returns403_forAnotherPrincipal() {
        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("user-1")))
                .get()
                .uri("/notifications/subscribe/user-2")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void broadcast_returnsOk() {
        NotificationRequest notification = new NotificationRequest("id-1", "user-1", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin-1")))
                .post()
                .uri("/notifications/broadcast")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void sendToUser_returnsDelivered_whenOnline() {
        NotificationRequest notification = new NotificationRequest("id-1", "user-1", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        when(resilientNotificationService.hasActiveConnection("user-1")).thenReturn(true);
        when(resilientNotificationService.sendNotification(any(NotificationRequest.class))).thenReturn(Mono.just(true));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin-1")))
                .post()
                .uri("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DELIVERED");
    }

    @Test
    void sendToUser_returnsAccepted_whenOffline() {
        NotificationRequest notification = new NotificationRequest("id-1", "user-1", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        when(resilientNotificationService.hasActiveConnection("user-1")).thenReturn(false);
        when(pendingNotificationService.addPendingNotification(any(NotificationRequest.class)))
                .thenReturn(Mono.just(true));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin-1")))
                .post()
                .uri("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void broadcast_returns403_forBuyer() {
        NotificationRequest notification = new NotificationRequest("id-1", "user-2", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("buyer-1")))
                .post()
                .uri("/notifications/broadcast")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void sendToUser_returns403_forBuyerTargetingAnotherUser() {
        NotificationRequest notification = new NotificationRequest("id-1", "user-2", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("buyer-1")))
                .post()
                .uri("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void sendToUser_returns401_whenNotAuthenticated() {
        NotificationRequest notification = new NotificationRequest("id-1", "user-1", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        webTestClient.post()
                .uri("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
