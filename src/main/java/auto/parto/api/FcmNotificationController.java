package auto.parto.api;

import auto.parto.dto.*;
import auto.parto.service.notification.*;
import com.google.firebase.FirebaseApp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications/fcm")
@RequiredArgsConstructor
@Slf4j
@Validated
public class FcmNotificationController {

    private final ResilientFcmService fcmService;
    private final DeviceTokenService deviceTokenService;

    /**
     * Register device token from React Native app
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, ? extends Serializable>>> registerToken(
            @Valid @RequestBody RegisterTokenRequest request) {

        String userId = request.getUserId();
        log.info("Registering FCM token for user: {}, platform: {}", userId, request.getPlatform());

        return deviceTokenService.registerToken(userId, request)
                .map(success -> ResponseEntity.ok().body(
                        Map.of(
                                "status", "SUCCESS",
                                "message", "Device registered successfully",
                                "userId", userId,
                                "timestamp", LocalDateTime.now()
                        )
                ));
    }

    /**
     * Send notification to specific user (e.g., notify buyer about seller response)
     */
    @PostMapping("/send")
    //@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public Mono<ResponseEntity<FcmDeliveryResult>> sendToUser(
            @Valid @RequestBody FcmRequest request) {

        log.info("Sending FCM notification to user: {}, title: {}",
                request.getUserId(), request.getTitle());

        FcmNotification notification = FcmNotification.builder()
                .title(request.getTitle())
                .body(request.getBody())
                .data(request.getData())
                .priority(request.isUrgent() ?
                        FcmNotification.NotificationPriority.HIGH :
                        FcmNotification.NotificationPriority.NORMAL)
                .build();

        return fcmService.sendToUser(request.getUserId(), notification)
                .map(result -> {
                    if (result.isSuccess()) {
                        log.info("Notification sent successfully to user: {}, messageId: {}",
                                request.getUserId(), result.getMessageId());
                        return ResponseEntity.ok(result);
                    } else {
                        log.warn("Notification failed for user: {}, error: {}",
                                request.getUserId(), result.getError());
                        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error sending notification to user {}: {}",
                            request.getUserId(), error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(FcmDeliveryResult.failed(request.getUserId(), error.getMessage())));
                });
    }

    /**
     * Notify sellers about new car part search
     */
    @PostMapping("/notify/search")
    //@PreAuthorize("hasRole('BUYER')")
    public Mono<ResponseEntity<BatchFcmResult>> notifySellersAboutSearch(
            @Valid @RequestBody SearchNotificationRequest request) {

        log.info("Notifying sellers about new search: {} for {}",
                request.getPartName(), request.getCarMake());

        // Create search notification
        SearchNotification search = SearchNotification.builder()
                .id(request.getSearchId())
                .userId(request.getBuyerId())
                .partName(request.getPartName())
                .carMake(request.getCarMake())
                .carModel(request.getCarModel())
                .buyerLocation(request.getBuyerLocation())
                .maxPrice(request.getMaxPrice())
                .urgency(request.getUrgency())
                .searchTime(LocalDateTime.now())
                .build();

        // Find matching sellers (from your search service)
        List<String> sellerIds = findMatchingSellers(search);

        if (sellerIds.isEmpty()) {
            log.warn("No matching sellers found for search: {}", request.getSearchId());
            return Mono.just(ResponseEntity.ok(BatchFcmResult.builder()
                    .totalUsers(0)
                    .successfulUsers(0)
                    .failedUsers(0)
                    .results(List.of())
                    .build()));
        }

        log.info("Found {} matching sellers for search {}", sellerIds.size(), request.getSearchId());

        // Create FCM notification
        FcmNotification notification = FcmNotification.newSearchMatch(
                search.getPartName(),
                search.getBuyerLocation(),
                search.getId()
        );

        return fcmService.sendToUsers(sellerIds, notification)
                .map(result -> {
                    log.info("Search notification results: {}/{} successful",
                            result.getSuccessfulUsers(), result.getTotalUsers());
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(error -> {
                    log.error("Failed to notify sellers: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BatchFcmResult.builder()
                                    .totalUsers(sellerIds.size())
                                    .successfulUsers(0)
                                    .failedUsers(sellerIds.size())
                                    .build()));
                });
    }

    /**
     * Notify buyer about seller's response
     */
    @PostMapping("/notify/response")
    //@PreAuthorize("hasRole('SELLER')")
    public Mono<ResponseEntity<FcmDeliveryResult>> notifyBuyerAboutResponse(
            @Valid @RequestBody SellerResponseRequest request) {

        log.info("Seller {} responding to search {}",
                request.getSellerId(), request.getSearchId());

        FcmNotification notification = FcmNotification.sellerResponse(
                request.getSellerName(),
                request.getPartName(),
                request.getResponseId()
        );

        return fcmService.sendToUser(request.getBuyerId(), notification)
                .map(result -> {
                    if (result.isSuccess()) {
                        log.info("Buyer notified about seller response: {}", request.getResponseId());
                        return ResponseEntity.ok(result);
                    } else {
                        log.warn("Failed to notify buyer about response: {}", result.getError());
                        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
                    }
                });
    }

    /**
     * Send urgent notification (for price alerts, stock alerts, etc.)
     */
    @PostMapping("/urgent")
    //@PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<BatchFcmResult>> sendUrgentNotification(
            @Valid @RequestBody UrgentNotificationRequest request) {

        log.warn("Sending urgent notification: {}", request.getTitle());

        FcmNotification notification = FcmNotification.builder()
                .title("🚨 " + request.getTitle())
                .body(request.getMessage())
                .priority(FcmNotification.NotificationPriority.HIGH)
                .data(request.getMetadata())
                .channelId("urgent-alerts")
                .build();

        return fcmService.sendToUsers(request.getUserIds(), notification)
                .doOnSuccess(result ->
                        log.info("Urgent notification sent to {}/{} users",
                                result.getSuccessfulUsers(), result.getTotalUsers()))
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Failed to send urgent notification: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BatchFcmResult.builder()
                                    .totalUsers(request.getUserIds().size())
                                    .successfulUsers(0)
                                    .failedUsers(request.getUserIds().size())
                                    .build()));
                });
    }

    /**
     * Get user's notification delivery stats
     */
    @GetMapping("/stats/{userId}")
    //@PreAuthorize("#userId == authentication.principal.id or hasRole('ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> getUserStats(@PathVariable String userId) {
        return Mono.fromCallable(() -> {
            List<String> tokens = deviceTokenService.getActiveTokens(userId);

            return Map.<String, Object>of(
                    "userId", userId,
                    "activeTokens", tokens.size(),
                    "hasTokens", !tokens.isEmpty(),
                    "tokens", tokens.stream()
                            .map(t -> t.substring(0, 8) + "..." + t.substring(t.length() - 4))
                            .toList(),
                    "timestamp", LocalDateTime.now()
            );
        }).map(ResponseEntity::ok);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        return Mono.fromCallable(() -> {
            try {
                // Check if Firebase is initialized
                FirebaseApp.getInstance();

                return ResponseEntity.ok(Map.of(
                        "status", "UP",
                        "service", "FCM",
                        "timestamp", LocalDateTime.now(),
                        "firebase", "CONNECTED"
                ));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "status", "DOWN",
                                "error", e.getMessage(),
                                "timestamp", LocalDateTime.now()
                        ));
            }
        });
    }

    // Helper methods
    private List<String> findMatchingSellers(SearchNotification search) {
        // Implement your seller matching logic here
        // This should call your SearchMatchingService
        return List.of(); // Return list of seller IDs
    }
}
