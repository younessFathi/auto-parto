package auto.parto.api;

import auto.parto.dto.NotificationRequest;
import auto.parto.service.notification.PendingNotificationService;
import auto.parto.service.notification.ResilientNotificationService;
import auto.parto.service.notification.ResilientPendingNotificationService;
import auto.parto.service.notification.SseNotificationEvent;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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


    @Test
    void subscribe_returnsSseStream() {
        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("00000000-0000-0000-0000-000000000001")))
                .mutate().responseTimeout(java.time.Duration.ofSeconds(35)).build().get()
                .uri("/notifications/subscribe")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void legacySubscribeIsNotExposedUnderVersionedApiPrefix() {
        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("00000000-0000-0000-0000-000000000001")))
                .get()
                .uri("/api/v1/notifications/subscribe")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void subscribeToUser_returnsSseStream() {
        Sinks.Many<SseNotificationEvent> userSink = Sinks.many().multicast().onBackpressureBuffer();
        when(resilientNotificationService.registerConnection("00000000-0000-0000-0000-000000000001")).thenReturn(userSink);
        when(resilientPendingNotificationService.deliverPendingOnConnect("00000000-0000-0000-0000-000000000001")).thenReturn(Mono.empty());

        userSink.tryEmitNext(new SseNotificationEvent(
                java.util.UUID.randomUUID(),
                new NotificationRequest("id-1", "00000000-0000-0000-0000-000000000001", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED,
                        "test", System.currentTimeMillis())));
        userSink.tryEmitComplete();

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("00000000-0000-0000-0000-000000000001")))
                .get()
                .uri("/notifications/subscribe/00000000-0000-0000-0000-000000000001")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void subscribeToUser_returns403_forAnotherPrincipal() {
        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("00000000-0000-0000-0000-000000000001")))
                .get()
                .uri("/notifications/subscribe/user-2")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void broadcast_returnsOk() {
        NotificationRequest notification = new NotificationRequest("id-1", "00000000-0000-0000-0000-000000000001", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

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
        NotificationRequest notification = new NotificationRequest("id-1", "00000000-0000-0000-0000-000000000001", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        when(resilientNotificationService.hasActiveConnection("00000000-0000-0000-0000-000000000001")).thenReturn(true);
        when(resilientNotificationService.deliverDirect(any(NotificationRequest.class)))
                .thenReturn(Mono.just(new ResilientNotificationService.DirectDeliveryResult(
                        java.util.UUID.randomUUID(), true, true)));

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
        NotificationRequest notification = new NotificationRequest("id-1", "00000000-0000-0000-0000-000000000001", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        when(resilientNotificationService.hasActiveConnection("00000000-0000-0000-0000-000000000001")).thenReturn(false);
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
    void sendToUser_doesNotPersistASecondRecordWhenDirectDeliveryFails() {
        NotificationRequest notification = new NotificationRequest("id-1", "00000000-0000-0000-0000-000000000001",
                auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        when(resilientNotificationService.hasActiveConnection("00000000-0000-0000-0000-000000000001")).thenReturn(true);
        when(resilientNotificationService.deliverDirect(any(NotificationRequest.class)))
                .thenReturn(Mono.just(new ResilientNotificationService.DirectDeliveryResult(
                        java.util.UUID.randomUUID(), true, false)));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin-1")))
                .post()
                .uri("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody().jsonPath("$.status").isEqualTo("PENDING");

        verify(pendingNotificationService, never()).addPendingNotification(any(NotificationRequest.class));
    }

    @Test
    void broadcast_returns403_forBuyer() {
        NotificationRequest notification = new NotificationRequest("id-1", "00000000-0000-0000-0000-000000000002", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

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
        NotificationRequest notification = new NotificationRequest("id-1", "00000000-0000-0000-0000-000000000002", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

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
        NotificationRequest notification = new NotificationRequest("id-1", "00000000-0000-0000-0000-000000000001", auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "test", System.currentTimeMillis());

        webTestClient.post()
                .uri("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification)
                .exchange()
                .expectStatus().isUnauthorized();
    }
    @Test
    @WithMockUser
    void legacySubscribeRequiresCanonicalPrincipalForPreferenceEvaluation() {
        webTestClient.get().uri("/notifications/subscribe").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void onlineOptOutReportsSuppressedWithoutClaimingDeliveredOrQueuingAnotherIntent() {
        NotificationRequest notification = new NotificationRequest("safe", "00000000-0000-0000-0000-000000000001",
                auto.parto.enums.NotificationType.SELLER_RESPONSE, "safe", 1L);
        when(resilientNotificationService.hasActiveConnection("00000000-0000-0000-0000-000000000001")).thenReturn(true);
        when(resilientNotificationService.deliverDirect(any())).thenReturn(Mono.just(
                new ResilientNotificationService.DirectDeliveryResult(java.util.UUID.randomUUID(), true, false, true)));
        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin-1")))
                .post().uri("/api/v1/notifications/send").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notification).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("SUPPRESSED");
        verify(pendingNotificationService, never()).addPendingNotification(any());
    }

    @Test
    void missingTypeCannotEnterAdminSendOrBroadcast() {
        NotificationRequest notification = new NotificationRequest("safe", "00000000-0000-0000-0000-000000000001", null, "safe", 1L);
        for (String route : new String[]{"/api/v1/notifications/send", "/api/v1/notifications/broadcast"}) {
            webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin-1")))
                    .post().uri(route).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(notification).exchange().expectStatus().isBadRequest();
        }
        verify(resilientNotificationService, never()).deliverDirect(any());
        verify(pendingNotificationService, never()).addPendingNotification(any());
    }

    @Test
    void uppercaseRecipientUsesCanonicalLiveConnectionAndPayload() {
        String canonical = "abcdefab-abcd-abcd-abcd-abcdefabcdef";
        NotificationRequest request = new NotificationRequest("upper", canonical.toUpperCase(java.util.Locale.ROOT),
                auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        when(resilientNotificationService.hasActiveConnection(canonical)).thenReturn(true);
        when(resilientNotificationService.deliverDirect(any())).thenReturn(Mono.just(
                new ResilientNotificationService.DirectDeliveryResult(java.util.UUID.randomUUID(), true, true)));
        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin"))).post()
                .uri("/api/v1/notifications/send").contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.userId").isEqualTo(canonical);
        org.mockito.Mockito.verify(resilientNotificationService).deliverDirect(new NotificationRequest("upper", canonical, request.type(), "safe", 1L));
    }

    @Test
    void abbreviatedRecipientIsBadRequestBeforeSseDeliveryOrPersistence() {
        NotificationRequest request = new NotificationRequest("short", "12345678-1234-4abc-8def-1",
                auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin"))).post()
                .uri("/api/v1/notifications/send").contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                .exchange().expectStatus().isBadRequest();
        org.mockito.Mockito.verifyNoInteractions(resilientNotificationService, pendingNotificationService);
    }

}
