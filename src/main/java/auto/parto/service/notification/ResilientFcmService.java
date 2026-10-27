package auto.parto.service.notification;

import auto.parto.exception.NoActiveTokensException;
import auto.parto.config.ProviderCallTelemetry;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

@Service
@ConditionalOnProperty(name = "app.firebase.components-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ResilientFcmService {

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenService deviceTokenService;
    private final CircuitBreaker circuitBreaker;
    private final ProviderCallTelemetry telemetry;
    private final Duration fcmTimeout;
    private final Duration retryBackoff;
    private final int maxRetries;
    private final int batchConcurrency;
    private final Semaphore inFlight;

    public ResilientFcmService(FirebaseMessaging firebaseMessaging,
                               DeviceTokenService deviceTokenService,
                               CircuitBreaker circuitBreaker,
                               ProviderCallTelemetry telemetry,
                               @Value("${auto.parto.providers.firebase.timeout:${firebase.messaging.timeout:10s}}") Duration fcmTimeout,
                               @Value("${auto.parto.providers.firebase.retry-backoff:1s}") Duration retryBackoff,
                               @Value("${auto.parto.providers.firebase.max-retries:3}") int maxRetries,
                               @Value("${auto.parto.providers.firebase.max-in-flight:64}") int maxInFlight,
                               @Value("${auto.parto.providers.firebase.batch-concurrency:32}") int batchConcurrency) {
        if (maxRetries < 0 || maxRetries > 10) throw new IllegalArgumentException("maxRetries must be between 0 and 10");
        if (fcmTimeout == null || fcmTimeout.isZero() || fcmTimeout.isNegative()) {
            throw new IllegalArgumentException("fcmTimeout must be positive");
        }
        if (retryBackoff == null || retryBackoff.isZero() || retryBackoff.isNegative()) {
            throw new IllegalArgumentException("retryBackoff must be positive");
        }
        if (maxInFlight < 1 || maxInFlight > 512) throw new IllegalArgumentException("maxInFlight must be between 1 and 512");
        if (batchConcurrency < 1 || batchConcurrency > maxInFlight) {
            throw new IllegalArgumentException("batchConcurrency must be between 1 and maxInFlight");
        }
        this.firebaseMessaging = firebaseMessaging;
        this.deviceTokenService = deviceTokenService;
        this.circuitBreaker = circuitBreaker;
        this.telemetry = telemetry;
        this.fcmTimeout = fcmTimeout;
        this.retryBackoff = retryBackoff;
        this.maxRetries = maxRetries;
        this.batchConcurrency = batchConcurrency;
        this.inFlight = new Semaphore(maxInFlight);
    }

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
                    log.warn("No active notification tokens found");
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
                    log.error("FCM delivery failed; notification retry policy will handle the result");
                    return Mono.just(FcmDeliveryResult.failed(userId, "PROVIDER_UNAVAILABLE"));
                });
    }

    /**
     * Helper to iterate over tokens and collect results
     */
    private Mono<FcmDeliveryResult> sendToAllTokens(String userId, List<String> tokens, FcmNotification notification) {
        return Flux.fromIterable(tokens)
                .flatMap(token -> sendToToken(token, notification), batchConcurrency)
                .collectList()
                .map(results -> buildDeliveryResult(userId, tokens.size(), results));
    }

    /**
     * Helper to build the final result object
     */
    private FcmDeliveryResult buildDeliveryResult(String userId, int totalTokens, List<FcmDeliveryResult> results) {
        boolean anySuccess = results.stream().anyMatch(FcmDeliveryResult::isSuccess);

        long failedCount = results.stream().filter(result -> !result.isSuccess()).count();

        return FcmDeliveryResult.builder()
                .userId(userId)
                .success(anySuccess)
                .failedTokens(List.of())
                .totalTokens(totalTokens)
                .successfulTokens(totalTokens - Math.toIntExact(failedCount))
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
                                new BatchFcmResult.UserDeliveryResult(userId, FcmDeliveryResult.failed(userId, "PROVIDER_UNAVAILABLE"), null)
                        ))
                , batchConcurrency)
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
    Mono<FcmDeliveryResult> sendToToken(String token, FcmNotification notification) {
        Mono<FcmDeliveryResult> sendTask = telemetry.observe("firebase", "send_message", Mono.defer(() -> {
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
                    if (!inFlight.tryAcquire()) {
                        return Mono.error(new RejectedExecutionException("Firebase in-flight limit reached"));
                    }
                    return sendAsync(message)
                            .map(messageId -> FcmDeliveryResult.success(token, messageId))
                            .doFinally(ignored -> inFlight.release());
                }), fcmTimeout)
                .retryWhen(Retry.backoff(maxRetries, retryBackoff)
                        .filter(this::isRetryableError)
                        .doBeforeRetry(retry -> log.warn("Retrying FCM provider request attempt={}", retry.totalRetries() + 1)));

        return sendTask
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(throwable -> {
                    log.error("FCM provider request failed; result will be handled by notification retry policy");
                    if (isInvalidTokenError(throwable)) {
                        Mono<Void> invalidation = deviceTokenService.markTokenInvalid(token);
                        return (invalidation == null ? Mono.<Void>empty() : invalidation)
                                .onErrorResume(invalidationError -> Mono.empty())
                                .thenReturn(FcmDeliveryResult.failed(null, "PROVIDER_UNAVAILABLE"));
                    }

                    return Mono.just(FcmDeliveryResult.failed(null, "PROVIDER_UNAVAILABLE"));
                });
    }

    private Mono<String> sendAsync(Message message) {
        return Mono.create(sink -> {
            ApiFuture<String> future;
            try {
                future = firebaseMessaging.sendAsync(message);
            } catch (RuntimeException startupFailure) {
                sink.error(startupFailure);
                return;
            }
            sink.onCancel(() -> future.cancel(true));
            ApiFutures.addCallback(future, new ApiFutureCallback<>() {
                @Override
                public void onFailure(Throwable failure) {
                    sink.error(failure);
                }

                @Override
                public void onSuccess(String messageId) {
                    sink.success(messageId);
                }
            }, MoreExecutors.directExecutor());
        });
    }


    private boolean isRetryableError(Throwable throwable) {
        // Retry on network errors, timeouts, server errors
        if (throwable instanceof TimeoutException) {
            return true;
        }
        return throwable instanceof FirebaseMessagingException &&
                ((FirebaseMessagingException) throwable).getMessagingErrorCode() ==
                        MessagingErrorCode.INTERNAL;
    }

    private boolean isInvalidTokenError(Throwable throwable) {
        if (!(throwable instanceof FirebaseMessagingException fcmException)) {
            return false;
        }
        MessagingErrorCode code = fcmException.getMessagingErrorCode();
        return code == MessagingErrorCode.INVALID_ARGUMENT || code == MessagingErrorCode.UNREGISTERED;
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
