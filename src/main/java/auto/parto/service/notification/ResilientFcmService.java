package auto.parto.service.notification;

import auto.parto.exception.NoActiveTokensException;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResilientFcmService {

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenService deviceTokenService;
    private final CircuitBreaker circuitBreaker;
    private final RetryTemplate retryTemplate;
    private final PushNotificationQueue pushQueue;

    /**
     * Send notification to a single user
     */
    public Mono<FcmDeliveryResult> sendToUser(String userId, FcmNotification notification) {
        return deviceTokenService.getActiveTokens(userId)
                // 1. Filter ensures that if the list is empty, the Mono becomes empty
                .filter(tokens -> !tokens.isEmpty())
                // 2. Now switchIfEmpty will trigger correctly
                .switchIfEmpty(Mono.error(new NoActiveTokensException(
                        String.format("No active tokens for user %s", userId)
                )))
                // 3. Process the tokens // multi devices support
                .flatMap(tokens -> sendToAllTokens(userId, tokens, notification))
                // 4. Handle "No Tokens" gracefully (don't retry, just report failure)
                .onErrorResume(NoActiveTokensException.class, e -> {
                    log.warn("No active tokens found for user {}" , e.getMessage());
                    return Mono.just(FcmDeliveryResult.builder()
                            .userId(userId)
                            .success(false)
                            .totalTokens(0)
                            .successfulTokens(0)
                            .failedTokens(List.of())
                            .error("No active tokens found")
                            .build());
                })
                // 5. Handle unexpected errors (Queue for retry)
                .onErrorResume(e -> {
                    log.error("Failed to send FCM to user {}: {}", userId, e.getMessage());
                    return pushQueue.enqueueForRetry(userId, notification)
                            .thenReturn(FcmDeliveryResult.failed(userId, e.getMessage()));
                });
    }

    /**
     * Helper to iterate over tokens and collect results
     */
    private Mono<FcmDeliveryResult> sendToAllTokens(String userId, List<String> tokens, FcmNotification notification) {
        return Flux.fromIterable(tokens)
                .flatMap(token -> sendToToken(token, notification))
                .collectList()
                .map(results -> buildDeliveryResult(userId, tokens.size(), results));
    }

    /**
     * Helper to build the final result object
     */
    private FcmDeliveryResult buildDeliveryResult(String userId, int totalTokens, List<FcmDeliveryResult> results) {
        boolean anySuccess = results.stream().anyMatch(FcmDeliveryResult::isSuccess);

        List<String> failedTokens = results.stream()
                .filter(r -> !r.isSuccess())
                .map(FcmDeliveryResult::getToken)
                .toList();

        return FcmDeliveryResult.builder()
                .userId(userId)
                .success(anySuccess)
                .failedTokens(failedTokens)
                .totalTokens(totalTokens)
                .successfulTokens(totalTokens - failedTokens.size())
                .build();
    }

    /**
     * Send notification to multiple users (e.g., multiple sellers)
     */
    public Mono<BatchFcmResult> sendToUsers(List<String> userIds, FcmNotification notification) {
        return Flux.fromIterable(userIds)
                .flatMap(userId -> sendToUser(userId, notification)
                        .map(result -> new BatchFcmResult.UserDeliveryResult(userId, result, null))
                        .onErrorResume(e -> Mono.just(
                                new BatchFcmResult.UserDeliveryResult(userId, FcmDeliveryResult.failed(userId, e.getMessage()), null)
                        ))
                )
                .collectList()
                .map(results -> {
                    int successCount = (int) results.stream()
                            .filter(r -> r.getFcmDeliveryResult().isSuccess())
                            .count();

                    return BatchFcmResult.builder()
                            .totalUsers(userIds.size())
                            .successfulUsers(successCount)
                            .failedUsers(userIds.size() - successCount)
                            .results(results)
                            .build();
                });
    }


    /**
     * Send to single token with circuit breaker and retry
     */
    private Mono<FcmDeliveryResult> sendToToken(String token, FcmNotification notification) {
        // 1. Define the task with internal Retry logic
        Mono<FcmDeliveryResult> sendTask = Mono.fromCallable(() -> {
                    Message message = Message.builder()
                            .setToken(token)
                            .setNotification(com.google.firebase.messaging.Notification.builder()
                                    .setTitle(notification.getTitle())
                                    .setBody(notification.getBody())
                                    .build())
                            .putAllData(notification.getData())
                            .setAndroidConfig(getAndroidConfig(notification))
                            .setApnsConfig(getApnsConfig(notification))
                            .build();

                    String messageId = firebaseMessaging.send(message);

                    log.debug("FCM sent successfully to token: {}, messageId: {}",
                            deviceTokenService.maskToken(token), messageId);

                    return FcmDeliveryResult.success(token, messageId);
                })
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(this::isRetryableError)
                        .doBeforeRetry(retry ->
                                log.warn("Retrying FCM send to token: {}, attempt: {}",
                                        deviceTokenService.maskToken(token), retry.totalRetries())
                        ));

        // 2. Wrap the task with Circuit Breaker and Fallback
        return sendTask
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(throwable -> {
                    // Circuit open or non-retryable error
                    log.error("FCM circuit open or failed for token: {}, error: {}",
                            deviceTokenService.maskToken(token), throwable.getMessage());

                    // Handle invalid token
                    if (isInvalidTokenError(throwable)) {
                        deviceTokenService.markTokenInvalid(token);
                    }

                    // FIX: Added .getMessage() to fix the syntax error "throwable."
                    return Mono.just(FcmDeliveryResult.failed(token, throwable.getMessage()));
                });
    }


    private boolean isRetryableError(Throwable throwable) {
        // Retry on network errors, timeouts, server errors
        return throwable instanceof FirebaseMessagingException &&
                ((FirebaseMessagingException) throwable).getMessagingErrorCode() ==
                        MessagingErrorCode.INTERNAL;
    }

    private boolean isInvalidTokenError(Throwable throwable) {
        return throwable instanceof FirebaseMessagingException &&
                ((FirebaseMessagingException) throwable).getMessagingErrorCode() ==
                        MessagingErrorCode.INVALID_ARGUMENT;
    }

    private AndroidConfig getAndroidConfig(FcmNotification notification) {
        String channelId = "default";
        if (notification.getPriority() == FcmNotification.NotificationPriority.HIGH) {
            channelId = "urgent-searches";
        }

        return AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setChannelId(channelId)
                        .setIcon("ic_notification")
                        .setColor("#FF5722") // Your brand color
                        .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                        .build())
                .build();
    }

    private ApnsConfig getApnsConfig(FcmNotification notification) {
        return ApnsConfig.builder()
                .setAps(Aps.builder()
                        .setAlert(ApsAlert.builder()
                                .setTitle(notification.getTitle())
                                .setBody(notification.getBody())
                                .build())
                        .setSound("default")
                        .setBadge(1)
                        .build())
                .build();
    }
}