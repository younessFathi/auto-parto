package auto.parto.api;

import auto.parto.dto.*;
import auto.parto.exception.NoMatchigSellersFoundException;
import auto.parto.service.SellerService;
import auto.parto.service.notification.*;
import auto.parto.utils.SecurityHelper;
import com.google.firebase.FirebaseApp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/fcm")
@RequiredArgsConstructor
@Slf4j
@Validated
public class FcmNotificationController {

    public static final String UNAUTHORIZED_NOTIFICATION_ATTEMPT_BY_INACTIVE_USER = "Unauthorized notification attempt by inactive user: {} ";
    private final ResilientFcmService fcmService;
    private final DeviceTokenService deviceTokenService;
    private final SellerService sellerService;
    private final SecurityHelper securityHelper;

    /**
     * Register device token from React Native app
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, Object>>> registerToken(
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
    @PreAuthorize("hasAnyRole('SELLER', 'BUYER' ,  'ADMIN')")
    public Mono<ResponseEntity<?>> sendToUser(
            @Valid @RequestBody FcmRequest request,
            Authentication authentication) {

        return securityHelper.validateAuthentication(authentication)
                        .flatMap(isValid->{
                            Mono<ResponseEntity<Object>> UNAUTHORIZED = checkAuthentication(authentication.getName(), isValid);
                            if (UNAUTHORIZED != null) return UNAUTHORIZED;
                            log.info("Sending FCM notification to user: {}, title: {}",
                                    request.getUserId(), request.getTitle());

                            FcmNotification notification = buildFcmNotification(request);
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
                        });
    }

    private static FcmNotification buildFcmNotification(FcmRequest request) {
        return FcmNotification.builder()
                .title(request.getTitle())
                .body(request.getBody())
                .data(request.getData())
                .priority(request.isUrgent() ?
                        FcmNotification.NotificationPriority.HIGH :
                        FcmNotification.NotificationPriority.NORMAL)
                .build();
    }

    /**
     * Notify sellers about new car part search
     */
    @PostMapping("/notify/search")
    @PreAuthorize("hasRole('BUYER')")
    public Mono<ResponseEntity<?>> notifySellersAboutSearch(
            @Valid @RequestBody SearchNotificationRequest request,
            Authentication authentication) {

        return securityHelper.validateBuyer(authentication).flatMap(
                isValid -> {
                    Mono<ResponseEntity<Object>> UNAUTHORIZED = checkAuthentication(authentication.getName(), isValid);
                    if (UNAUTHORIZED != null) return UNAUTHORIZED;
                    log.info("Notifying sellers about new search: {} for {}",
                            request.getPartName(), request.getCarMake());

                    // Create search notification
                    SearchNotification search = buildSearchNotification(request);

                    // Find matching sellers (from your search service)
                    return sellerService.findMatchingSellers()
                            .filter(sellersId -> !sellersId.isEmpty())
                            .switchIfEmpty(Mono.error(new NoMatchigSellersFoundException("No matching sellers found")))
                            .flatMap(sellersId ->
                                    fcmService.sendToUsers(sellersId, buildFcmNotification(search))
                                            .map(result -> {
                                                log.info("Search notification results: {}/{} successful",
                                                        result.getSuccessfulUsers(), result.getTotalUsers());
                                                return ResponseEntity.ok(result);
                                            })
                                            .onErrorResume(error -> {
                                                log.error("Failed to notify sellers: {}", error.getMessage(), error);
                                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(BatchFcmResult.builder()
                                                                .totalUsers(sellersId.size())
                                                                .successfulUsers(0)
                                                                .failedUsers(sellersId.size())
                                                                .build()));
                                            })
                            );
                }
        );
    }

    @Nullable
    private static Mono<ResponseEntity<Object>> checkAuthentication(String name, Boolean isValid) {
        if (!isValid) {
            log.warn(UNAUTHORIZED_NOTIFICATION_ATTEMPT_BY_INACTIVE_USER, name);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return null;
    }

    private static SearchNotification buildSearchNotification(SearchNotificationRequest request) {
       return SearchNotification.builder()
                .id(UUID.randomUUID().toString())
                .userId(request.getBuyerId())
                .partName(request.getPartName())
                .carMake(request.getCarMake())
                .carModel(request.getCarModel())
                .buyerLocation(request.getBuyerLocation())
                .maxPrice(request.getMaxPrice())
                .urgency(request.getUrgency())
                .searchTime(LocalDateTime.now())
                .imageUrl(request.getImageUrl())
                .build();
    }

    private static FcmNotification buildFcmNotification(SearchNotification search) {
        log.info("Building FCM notification for search: {}" , search);
        return FcmNotification.newSearchMatch(
                search.getPartName(),
                search.getBuyerLocation(),
                UUID.randomUUID().toString(),
                search.getUserId()
        );
    }

    /**
     * Notify buyer about seller's response
     */
    @PostMapping("/notify/response")
    @PreAuthorize("hasRole('SELLER')")
    public Mono<ResponseEntity<?>> notifyBuyerAboutResponse(
            @Valid @RequestBody SellerResponseRequest request,
            Authentication authentication) {
       return securityHelper.validateSeller(authentication)
                        .flatMap(isValid -> {
                            Mono<ResponseEntity<Object>> UNAUTHORIZED = checkAuthentication(authentication.getName(), isValid);
                            if (UNAUTHORIZED != null) return UNAUTHORIZED;

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
                        });
    }

    /**
     * Send urgent notification (for price alerts, stock alerts, etc.)
     */
    @PostMapping("/urgent")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<?>> sendUrgentNotification(
            @Valid @RequestBody UrgentNotificationRequest request,
            Authentication authentication) {

        return securityHelper.validateAdmin(authentication)
                .flatMap(
                        isValid -> {
                            Mono<ResponseEntity<Object>> UNAUTHORIZED = checkAuthentication(authentication.getName(), isValid);
                            if (UNAUTHORIZED != null) return UNAUTHORIZED;
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
                );
    }

    /**
     * Get user's notification delivery stats
     */
    @GetMapping("/stats/{userId}")
    @PreAuthorize("#userId == authentication.principal.id or hasRole('ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> getUserStats(@PathVariable String userId) {
        return deviceTokenService.getActiveTokens(userId)
                .map(tokens -> ResponseEntity.ok().body(
                        Map.of(
                                "userId", userId,
                                "activeTokens", tokens.size(),
                                "hasTokens", !tokens.isEmpty(),
                                "tokens", tokens.stream()
                                        .map(t -> t.substring(0, 8) + "..." + t.substring(t.length() - 4))
                                        .toList(),
                                "timestamp", LocalDateTime.now()
                        )));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<?>> healthCheck(Authentication authentication) {
        return securityHelper.validateAdmin(authentication)
                .flatMap(isValid ->{
                    Mono<ResponseEntity<Object>> UNAUTHORIZED = checkAuthentication(authentication.getName(), isValid);
                    if (UNAUTHORIZED != null) return UNAUTHORIZED;
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
                });

    }
}
