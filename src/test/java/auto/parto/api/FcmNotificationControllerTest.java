package auto.parto.api;

import auto.parto.dto.*;
import auto.parto.service.SellerService;
import auto.parto.service.notification.DeviceTokenService;
import auto.parto.service.notification.FcmDeliveryResult;
import auto.parto.service.notification.BatchFcmResult;
import auto.parto.service.notification.ResilientFcmService;
import auto.parto.advicer.OtpExceptionHandler;
import auto.parto.utils.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

@WebFluxTest(controllers = FcmNotificationController.class)
@Import({TestConfig.class, OtpExceptionHandler.class})
class FcmNotificationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ResilientFcmService fcmService;

    @MockitoBean
    private DeviceTokenService deviceTokenService;

    @MockitoBean
    private SellerService sellerService;

    @MockitoBean
    private SecurityHelper securityHelper;

    @Test
    void registerToken_returns200() {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setDeviceToken("fcm-token-123");
        request.setPlatform("android");
        request.setUserId("user-1");

        when(deviceTokenService.registerToken(anyString(), any(RegisterTokenRequest.class)))
                .thenReturn(Mono.just(true));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("user-1")))
                .post()
                .uri("/api/v1/notifications/fcm/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.userId").isEqualTo("user-1");
    }

    @Test
    @WithMockUser
    void registerToken_returns400_whenTokenMissing() {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setPlatform("android");

        webTestClient.post()
                .uri("/api/v1/notifications/fcm/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void sendToUser_returns200() {
        FcmRequest request = FcmRequest.builder()
                .userId("user-1")
                .title("Test")
                .body("Message")
                .build();

        when(securityHelper.validateAuthentication(any())).thenReturn(Mono.just(true));
        when(fcmService.sendToUser(anyString(), any())).thenReturn(Mono.just(
                FcmDeliveryResult.success("user-1", "msg-1")));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin-1")))
                .post()
                .uri("/api/v1/notifications/fcm/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void sendToUser_returns401_whenNotAuthenticated() {
        FcmRequest request = FcmRequest.builder()
                .userId("user-1")
                .title("Test")
                .body("Message")
                .build();

        when(securityHelper.validateAuthentication(any())).thenReturn(Mono.just(false));

        webTestClient.post()
                .uri("/api/v1/notifications/fcm/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void sendToUser_returns403_forNonAdminPrincipal() {
        FcmRequest request = FcmRequest.builder()
                .userId("user-2")
                .title("Test")
                .body("Message")
                .build();

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("buyer-1")))
                .post()
                .uri("/api/v1/notifications/fcm/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void notifySellersAboutSearch_returns200() {
        SearchNotificationRequest request = SearchNotificationRequest.builder()
                .buyerId("buyer-1")
                .partName("Brake Pads")
                .carMake("Toyota")
                .buyerLocation("Casablanca")
                .build();

        when(securityHelper.validateBuyer(any())).thenReturn(Mono.just(true));
        when(sellerService.findMatchingSellers()).thenReturn(Mono.just(List.of("seller-1", "seller-2")));
        when(fcmService.sendToUsers(any(), any())).thenReturn(Mono.just(
                BatchFcmResult.builder().totalUsers(2).successfulUsers(2).failedUsers(0).build()));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("buyer-1")))
                .post()
                .uri("/api/v1/notifications/fcm/notify/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void notifySellersAboutSearch_returns404_whenNoMatchingSellers() {
        SearchNotificationRequest request = SearchNotificationRequest.builder()
                .buyerId("buyer-1")
                .partName("Brake Pads")
                .carMake("Toyota")
                .buyerLocation("Casablanca")
                .build();

        when(securityHelper.validateBuyer(any())).thenReturn(Mono.just(true));
        when(sellerService.findMatchingSellers()).thenReturn(Mono.just(List.of()));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("buyer-1")))
                .post()
                .uri("/api/v1/notifications/fcm/notify/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void notifyBuyerAboutResponse_returns200_forAdminDispatch() {
        SellerResponseRequest request = SellerResponseRequest.builder()
                .buyerId("buyer-1")
                .sellerId("seller-1")
                .sellerName("Shop")
                .partName("Brake Pads")
                .responseId("resp-1")
                .searchId("search-1")
                .build();

        when(securityHelper.validateAdmin(any())).thenReturn(Mono.just(true));
        when(fcmService.sendToUser(anyString(), any())).thenReturn(Mono.just(
                FcmDeliveryResult.success("buyer-1", "msg-1")));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin-1")))
                .post()
                .uri("/api/v1/notifications/fcm/notify/response")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void notifyBuyerAboutResponse_returns403_forSellerWithoutPersistedRelationship() {
        SellerResponseRequest request = SellerResponseRequest.builder()
                .buyerId("buyer-1")
                .sellerId("seller-1")
                .responseId("response-1")
                .searchId("search-1")
                .build();

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.seller("seller-1")))
                .post()
                .uri("/api/v1/notifications/fcm/notify/response")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sendUrgentNotification_returns200() {
        UrgentNotificationRequest request = UrgentNotificationRequest.builder()
                .userIds(List.of("user-1", "user-2"))
                .title("Urgent Alert")
                .message("Critical issue")
                .metadata(Map.of("key", "value"))
                .build();

        when(securityHelper.validateAdmin(any())).thenReturn(Mono.just(true));
        when(fcmService.sendToUsers(any(), any())).thenReturn(Mono.just(
                BatchFcmResult.builder().totalUsers(2).successfulUsers(2).failedUsers(0).build()));

        webTestClient.post()
                .uri("/api/v1/notifications/fcm/urgent")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getUserStats_returns200() {
        when(deviceTokenService.getActiveTokens("user-1")).thenReturn(Mono.just(List.of("abcdefghijklmnop", "qrstuvwxyz123456")));

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("user-1")))
                .get()
                .uri("/api/v1/notifications/fcm/stats/user-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("user-1")
                .jsonPath("$.activeTokens").isEqualTo(2)
                .jsonPath("$.hasTokens").isEqualTo(true);
    }

    @Test
    void registerToken_returns403_whenUserIdBelongsToAnotherPrincipal() {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setDeviceToken("fcm-token-123");
        request.setPlatform("android");
        request.setUserId("user-2");

        webTestClient.mutateWith(mockAuthentication(CanonicalTestAuthentication.buyer("user-1")))
                .post()
                .uri("/api/v1/notifications/fcm/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @WithMockUser
    void healthCheck_returnsServiceUnavailable_whenFirebaseNotInitialized() {
        when(securityHelper.validateAdmin(any())).thenReturn(Mono.just(true));

        webTestClient.get()
                .uri("/api/v1/notifications/fcm/health")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void registerToken_returns401_whenNotAuthenticated() {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setDeviceToken("fcm-token");
        request.setPlatform("android");

        webTestClient.post()
                .uri("/api/v1/notifications/fcm/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
