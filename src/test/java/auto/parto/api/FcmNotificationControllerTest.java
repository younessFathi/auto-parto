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
    @WithMockUser
    void registerToken_returns200() {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setDeviceToken("fcm-token-123");
        request.setPlatform("android");
        request.setUserId("user-1");

        when(deviceTokenService.registerToken(anyString(), any(RegisterTokenRequest.class)))
                .thenReturn(Mono.just(true));

        webTestClient.post()
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
    @WithMockUser(roles = "BUYER")
    void sendToUser_returns200() {
        FcmRequest request = FcmRequest.builder()
                .userId("user-1")
                .title("Test")
                .body("Message")
                .build();

        when(securityHelper.validateAuthentication(any())).thenReturn(Mono.just(true));
        when(fcmService.sendToUser(anyString(), any())).thenReturn(Mono.just(
                FcmDeliveryResult.success("user-1", "msg-1")));

        webTestClient.post()
                .uri("/api/v1/notifications/fcm/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(roles = "BUYER")
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
    @WithMockUser(roles = "BUYER")
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

        webTestClient.post()
                .uri("/api/v1/notifications/fcm/notify/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void notifySellersAboutSearch_returns404_whenNoMatchingSellers() {
        SearchNotificationRequest request = SearchNotificationRequest.builder()
                .buyerId("buyer-1")
                .partName("Brake Pads")
                .carMake("Toyota")
                .buyerLocation("Casablanca")
                .build();

        when(securityHelper.validateBuyer(any())).thenReturn(Mono.just(true));
        when(sellerService.findMatchingSellers()).thenReturn(Mono.just(List.of()));

        webTestClient.post()
                .uri("/api/v1/notifications/fcm/notify/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void notifyBuyerAboutResponse_returns200() {
        SellerResponseRequest request = SellerResponseRequest.builder()
                .buyerId("buyer-1")
                .sellerId("seller-1")
                .sellerName("Shop")
                .partName("Brake Pads")
                .responseId("resp-1")
                .searchId("search-1")
                .build();

        when(securityHelper.validateSeller(any())).thenReturn(Mono.just(true));
        when(fcmService.sendToUser(anyString(), any())).thenReturn(Mono.just(
                FcmDeliveryResult.success("buyer-1", "msg-1")));

        webTestClient.post()
                .uri("/api/v1/notifications/fcm/notify/response")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
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
    @WithMockUser
    void getUserStats_returns200() {
        when(deviceTokenService.getActiveTokens("user-1")).thenReturn(Mono.just(List.of("abcdefghijklmnop", "qrstuvwxyz123456")));

        webTestClient.get()
                .uri("/api/v1/notifications/fcm/stats/user-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("user-1")
                .jsonPath("$.activeTokens").isEqualTo(2)
                .jsonPath("$.hasTokens").isEqualTo(true);
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
