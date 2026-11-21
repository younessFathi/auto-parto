package auto.parto.persistence;

import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcNotificationRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private NotificationRepository repository;
    private TransactionalOperator transactions;

    @BeforeAll
    void migrateAndCreateRepository() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var connectionFactory = io.r2dbc.spi.ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql").option(HOST, POSTGRES.getHost()).option(PORT, POSTGRES.getFirstMappedPort())
                .option(USER, POSTGRES.getUsername()).option(PASSWORD, POSTGRES.getPassword())
                .option(DATABASE, POSTGRES.getDatabaseName()).build());
        databaseClient = DatabaseClient.create(connectionFactory);
        transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        repository = new R2dbcNotificationRepository(databaseClient, transactions);
    }

    @Test
    void enqueuesAndListsPendingNotification() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "OFFER_CREATED", "{\"offerId\":\"abc\"}")))
                .assertNext(notification -> {
                    assertThat(notification.recipientUserPublicId()).isEqualTo(owner);
                    assertThat(notification.eventType()).isEqualTo("OFFER_CREATED");
                    assertThat(notification.payloadJson()).contains("offerId");
                    assertThat(notification.status()).isEqualTo("PENDING");
                }).verifyComplete();
    }

    @Test
    void enqueueCommitsNotificationAndVersionedOutboxEventTogether() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "OFFER_CREATED", "{\"offerId\":\"abc\"}"))
                        .flatMap(notification -> databaseClient.sql("""
                                        SELECT event_type, schema_version, aggregate_public_id,
                                               payload::text AS payload, count(delivery.id) AS delivery_count
                                        FROM outbox_events event
                                        JOIN outbox_deliveries delivery ON delivery.outbox_event_id = event.id
                                        WHERE event.aggregate_public_id = :notificationId
                                        GROUP BY event.event_type, event.schema_version,
                                                 event.aggregate_public_id, event.payload
                                        """)
                                .bind("notificationId", notification.publicId())
                                .map((row, metadata) -> new OutboxSnapshot(
                                        row.get("event_type", String.class),
                                        row.get("schema_version", Integer.class),
                                        row.get("aggregate_public_id", UUID.class),
                                        row.get("payload", String.class),
                                        row.get("delivery_count", Long.class)))
                                .one()))
                .assertNext(snapshot -> {
                    assertThat(snapshot.eventType()).isEqualTo("notification.requested");
                    assertThat(snapshot.schemaVersion()).isEqualTo(1);
                    assertThat(snapshot.aggregatePublicId()).isNotNull();
                    assertThat(snapshot.payload()).contains("notificationPayload", "offerId");
                    assertThat(snapshot.deliveryCount()).isEqualTo(1L);
                }).verifyComplete();
    }

    @Test
    void statusUpdateIsIdempotentAndPendingReadsAreBounded() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "TEST", "{}"))
                        .flatMap(notification -> repository.updateStatus(notification.publicId(), "DISPATCHED")
                                .zipWith(repository.updateStatus(notification.publicId(), "DISPATCHED"))))
                .assertNext(result -> assertThat(result.getT1()).isTrue())
                .verifyComplete();
        StepVerifier.create(repository.findPending(100)
                        .filter(notification -> notification.eventType().equals("TEST")))
                .expectNextCount(0).verifyComplete();
    }

    @Test
    void missingRecipientDoesNotCreateNotification() {
        StepVerifier.create(repository.enqueue(UUID.randomUUID(), "TEST", "{}"))
                .verifyComplete();
    }

    @Test
    void rejectsPayloadsLargerThanTheNotificationBoundary() {
        String oversizedPayload = "{\"message\":\"" + "x".repeat(NotificationPayloadLimits.MAX_SERIALIZED_BYTES)
                + "\"}";

        assertThatThrownBy(() -> repository.enqueue(UUID.randomUUID(), "TEST", oversizedPayload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("notification payload exceeds the bounded size");
    }

    @Test
    void outboxFailureRollsBackNotificationAndEvent() {
        UUID owner = UUID.randomUUID();
        insertUser(owner).block();
        createFailingOutboxTrigger().block();
        try {
            StepVerifier.create(repository.enqueue(owner, "ROLLBACK_TEST", "{}"))
                .expectErrorMatches(error -> error.getMessage() != null
                        && error.getMessage().contains("forced notification outbox failure"))
                    .verify();
        } finally {
            dropFailingOutboxTrigger().block();
        }

        assertThat(countNotifications(owner, "ROLLBACK_TEST")).isZero();
        assertThat(countOutboxEvents("notification.requested", "ROLLBACK_TEST")).isZero();
    }

    @Test
    void recordsDeliveryAttemptForExistingNotification() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "TEST", "{}"))
                        .flatMap(notification -> repository.recordAttempt(notification.publicId(), "FCM", "ACCEPTED",
                                "provider-1", "")))
                .assertNext(attempt -> {
                    assertThat(attempt.channel()).isEqualTo("FCM");
                    assertThat(attempt.status()).isEqualTo("ACCEPTED");
                    assertThat(attempt.providerReference()).isEqualTo("provider-1");
                    assertThat(attempt.completedAt()).isNotNull();
                }).verifyComplete();
    }

    @Test
    void readsAndMarksOnlyTheRecipientPendingNotifications() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(insertUser(otherOwner))
                        .then(repository.enqueue(owner, "OWNER_EVENT", "{}"))
                        .then(repository.enqueue(otherOwner, "OTHER_EVENT", "{}"))
                        .thenMany(repository.findPendingForRecipient(owner, 100).map(NotificationRecord::eventType).collectList())
                        .flatMap(events -> repository.markPendingForRecipient(owner).map(marked -> java.util.Map.entry(events, marked))))
                .assertNext(result -> {
                    assertThat(result.getKey()).containsExactly("OWNER_EVENT");
                    assertThat(result.getValue()).isEqualTo(1L);
                }).verifyComplete();
        StepVerifier.create(repository.findPending(100)
                        .filter(notification -> notification.eventType().equals("OWNER_EVENT")))
                .expectNextCount(0).verifyComplete();
        StepVerifier.create(repository.findPending(100)
                        .filter(notification -> notification.eventType().equals("OTHER_EVENT")))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    void replaysAfterCursorWithStableUuidTieBreaker() {
        UUID owner = UUID.randomUUID();
        insertUser(owner).then(repository.enqueue(owner, "REPLAY_ONE", "{}"))
                .then(repository.enqueue(owner, "REPLAY_TWO", "{}"))
                .block();
        var records = repository.findPendingForRecipient(owner, 100)
                .filter(record -> record.eventType().startsWith("REPLAY_"))
                .collectList().block();
        databaseClient.sql("UPDATE notifications SET created_at = TIMESTAMP '2020-01-01 00:00:00+00' WHERE event_type LIKE 'REPLAY_%'")
                .fetch().rowsUpdated().block();
        var orderedIds = databaseClient.sql("""
                        SELECT notification.public_id
                        FROM notifications notification
                        JOIN app_users owner ON owner.id = notification.recipient_user_id
                        WHERE owner.public_id = :owner AND notification.event_type LIKE 'REPLAY_%'
                        ORDER BY notification.created_at, notification.public_id
                        """)
                .bind("owner", owner)
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .all().collectList().block();

        repository.markPendingForRecipient(owner).block();
        var actual = repository.findForRecipientAfter(owner, orderedIds.getFirst(), 100)
                .map(NotificationRecord::publicId).collectList().block();
        assertThat(actual).containsExactlyElementsOf(orderedIds.subList(1, orderedIds.size()));
    }

    @Test
    void directDeliveryClaimAndAcknowledgementCompletesOneDurableRecord() {
        UUID owner = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        NotificationRecord record = insertUser(owner)
                .then(repository.enqueue(owner, "DIRECT_DELIVERY", "{}"))
                .block();

        NotificationRecord claimed = repository.claimNotification(record.publicId(), claimId.toString(),
                        Instant.now(), Duration.ofSeconds(30))
                .block();
        assertThat(claimed.publicId()).isEqualTo(record.publicId());
        assertThat(repository.recordAttempt(record.publicId(), "SSE", "WRITTEN", null, null).block()).isNotNull();
        assertThat(repository.acknowledgeDelivery(record.publicId(), claimId.toString()).block()).isTrue();
        assertThat(repository.acknowledgeDelivery(record.publicId(), claimId.toString()).block()).isFalse();
        assertThat(databaseClient.sql("SELECT status FROM notifications WHERE public_id = :id")
                .bind("id", record.publicId())
                .map((row, metadata) -> row.get("status", String.class)).one().block()).isEqualTo("DISPATCHED");
    }

    @Test
    void failedDirectDeliveryClearsLeaseAndKeepsTheSameRowRetryable() {
        UUID owner = UUID.randomUUID();
        NotificationRecord record = insertUser(owner)
                .then(repository.enqueue(owner, "DIRECT_RETRY", "{}"))
                .block();

        assertThat(repository.claimNotification(record.publicId(), "direct-worker", Instant.now(),
                Duration.ofSeconds(30)).block()).isNotNull();
        assertThat(repository.recordAttempt(record.publicId(), "SSE", "FAILED", null,
                "DELIVERY_ERROR").block()).isNotNull();
        assertThat(repository.scheduleRetry(record.publicId(), "direct-worker", Instant.now().plusSeconds(30),
                "DELIVERY_ERROR", 3).block()).isTrue();

        assertThat(databaseClient.sql("""
                        SELECT status, lease_owner, attempt_count, last_failure_code
                        FROM notifications WHERE public_id = :id
                        """)
                .bind("id", record.publicId())
                .map((row, metadata) -> java.util.Arrays.asList(row.get("status", String.class),
                        row.get("lease_owner", String.class), row.get("attempt_count", Integer.class),
                        row.get("last_failure_code", String.class)))
                .one().block())
                .containsExactly("PENDING", null, 1, "DELIVERY_ERROR");
    }

    @Test
    void replayCursorMustBelongToRecipientAndExist() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        NotificationRecord record = insertUser(owner)
                .then(insertUser(otherOwner))
                .then(repository.enqueue(owner, "CURSOR_OWNER", "{}"))
                .block();

        StepVerifier.create(repository.cursorBelongsToRecipient(owner, record.publicId()))
                .expectNext(true).verifyComplete();
        StepVerifier.create(repository.cursorBelongsToRecipient(otherOwner, record.publicId()))
                .expectNext(false).verifyComplete();
        StepVerifier.create(repository.cursorBelongsToRecipient(owner, missing))
                .expectNext(false).verifyComplete();
    }

    @Test
    void marksOnlyTheDeliveredNotificationId() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "FIRST", "{\"id\":\"first\"}"))
                        .then(repository.enqueue(owner, "SECOND", "{\"id\":\"second\"}"))
                        .then(repository.markPendingForRecipientAndId(owner, "first")))
                .expectNext(true).verifyComplete();
        StepVerifier.create(repository.findPendingForRecipient(owner, 100)
                        .map(NotificationRecord::eventType))
                .expectNext("SECOND").verifyComplete();
    }

    @Test
    void countsPendingRecordsAndReturnsBoundedRecipientIds() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "STATS", "{}"))
                        .then(repository.countPending()))
                .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(1L))
                .verifyComplete();
        StepVerifier.create(repository.findPendingRecipientIds(100).filter(owner::equals))
                .expectNext(owner).verifyComplete();
    }

    @Test
    void concurrentClaimsHaveOneWinnerAndLeaseExpiryAllowsRecovery() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "CLAIM", "{}"))
                        .flatMap(notification -> {
                            Instant now = Instant.now().plusSeconds(1);
                            return Mono.zip(
                                repository.claimPendingForRecipient(owner, "worker-1", now,
                                        Duration.ofMinutes(1), 1).collectList(),
                                repository.claimPendingForRecipient(owner, "worker-2", now,
                                        Duration.ofMinutes(1), 1).collectList());
                        }))
                .assertNext(result -> assertThat(result.getT1().size() + result.getT2().size()).isEqualTo(1))
                .verifyComplete();

        StepVerifier.create(repository.claimPendingForRecipient(owner, "worker-2", Instant.now().plusSeconds(120),
                                Duration.ofMinutes(1), 1))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void retryStateIsDurableAndReachesTerminalExhaustion() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "RETRY", "{}"))
                        .flatMap(notification -> repository.claimPendingForRecipient(owner, "worker-retry",
                                        Instant.now(), Duration.ofMinutes(1), 1)
                                .single()
                                .flatMap(claimed -> repository.scheduleRetry(claimed.publicId(), "worker-retry",
                                        Instant.now().plusSeconds(30), "DELIVERY_UNAVAILABLE", 1))))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(databaseClient.sql("""
                        SELECT status, attempt_count, last_failure_code, lease_owner
                        FROM notifications WHERE event_type = 'RETRY'
                        ORDER BY id DESC LIMIT 1
                        """)
                        .map((row, metadata) -> java.util.List.of(row.get("status", String.class),
                                row.get("attempt_count", Integer.class), row.get("last_failure_code", String.class)))
                        .one())
                .assertNext(row -> assertThat(row).containsExactly("EXHAUSTED", 1,
                        "DELIVERY_UNAVAILABLE"))
                .verifyComplete();
    }

    @Test
    void claimLimitIsBounded() {
        assertThatThrownBy(() -> repository.claimPendingForRecipient(UUID.randomUUID(), "worker", Instant.now(),
                Duration.ofSeconds(1), 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    @Test
    void suppressionIsLeaseGuardedAtomicTerminalAndNeverReplayedOrAcknowledged() {
        UUID owner = UUID.randomUUID();
        insertUser(owner).block();
        NotificationRecord cursor = repository.enqueue(owner, "SELLER_RESPONSE", "{}").block();
        NotificationRecord target = repository.enqueue(owner, "SELLER_RESPONSE", "{}").block();
        repository.claimNotification(target.publicId(), "worker", Instant.now(), Duration.ofSeconds(30)).block();
        assertThat(repository.suppressDelivery(target.publicId(), "other", "PREFERENCE_DISABLED").block()).isFalse();
        assertThat(repository.suppressDelivery(target.publicId(), "worker", "PREFERENCE_DISABLED").block()).isTrue();
        assertThat(repository.acknowledgeDelivery(target.publicId(), "worker").block()).isFalse();
        assertThat(repository.suppressDelivery(target.publicId(), "worker", "PREFERENCE_DISABLED").block()).isFalse();
        assertThat(repository.findPendingForRecipient(owner, 100).collectList().block())
                .extracting(NotificationRecord::publicId).containsExactly(cursor.publicId());
        assertThat(repository.findForRecipientAfter(owner, cursor.publicId(), 100).collectList().block()).isEmpty();
        assertThat(databaseClient.sql("SELECT count(*) AS n FROM notification_attempts a JOIN notifications n ON n.id = a.notification_id WHERE n.public_id = :id AND a.status = 'SUPPRESSED'")
                .bind("id", target.publicId()).map((r,m) -> r.get("n", Long.class)).one().block()).isEqualTo(1);
        repository.claimNotification(cursor.publicId(), "expired", Instant.now(), Duration.ofSeconds(30)).block();
        databaseClient.sql("UPDATE notifications SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE public_id = :id")
                .bind("id", cursor.publicId()).fetch().rowsUpdated().block();
        assertThat(repository.suppressDelivery(cursor.publicId(), "expired", "PREFERENCE_DISABLED").block()).isFalse();
    }

    @Test
    void realPreferencesSuppressQueuedDirectAndReplayWhileLookupOutagesStayRetryable() {
        UUID owner = UUID.randomUUID();
        insertUser(owner).block();
        var prefs = new R2dbcNotificationPreferencesRepository(databaseClient, transactions);
        var filter = new auto.parto.service.notification.NotificationPreferenceFilter(
                new auto.parto.service.NotificationPreferencesService(prefs));
        var redis = org.mockito.Mockito.mock(auto.parto.service.notification.RedisNotificationStore.class);
        org.mockito.Mockito.when(redis.storePending(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(reactor.core.publisher.Mono.just(true));
        org.mockito.Mockito.when(redis.removeNotification(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(1L));
        org.mockito.Mockito.when(redis.replacePendingNotifications(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(reactor.core.publisher.Mono.empty());
        var provider = new org.springframework.beans.factory.ObjectProvider<NotificationRepository>() {
            public NotificationRepository getObject(Object... args) { return repository; }
            public NotificationRepository getIfAvailable() { return repository; }
            public NotificationRepository getIfUnique() { return repository; }
        };
        var store = new auto.parto.service.notification.HybridNotificationStore(redis, provider,
                new com.fasterxml.jackson.databind.ObjectMapper(), filter);
        var sse = org.mockito.Mockito.mock(auto.parto.service.notification.SSENotificationService.class);
        var breaker = org.mockito.Mockito.mock(auto.parto.service.BasicCircuitBreaker.class);
        var direct = new auto.parto.service.notification.ResilientNotificationService(breaker, sse, store);
        var request = new auto.parto.dto.NotificationRequest("safe-1", owner.toString(),
                auto.parto.enums.NotificationType.SELLER_RESPONSE, "safe", 1L);
        org.mockito.Mockito.when(sse.sendViaSSE(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var first = direct.deliverDirect(request).block();
        assertThat(first.delivered()).isTrue();
        var replay = direct.deliverDirect(new auto.parto.dto.NotificationRequest("safe-2", owner.toString(), request.type(), "safe", 2L)).block();
        assertThat(store.replayNotifications(owner.toString(), first.publicId(), 100).collectList().block()).hasSize(1);
        // A queued intent was accepted under defaults; a later type opt-out controls actual delivery.
        var queued = store.storePendingRecord(new auto.parto.dto.NotificationRequest("safe-3", owner.toString(), request.type(), "safe", 3L)).block();
        prefs.save(owner, new NotificationPreferencesRecord(null, owner, true, true, false,
                false, true, true, true, false, null, null)).block();
        assertThat(store.replayNotifications(owner.toString(), first.publicId(), 100).collectList().block()).isEmpty();
        var restartedWorker = new auto.parto.service.notification.ResilientPendingNotificationService(store, sse, breaker);
        assertThat(restartedWorker.deliverPendingOnConnect(owner.toString()).block()).isZero();
        assertThat(repository.findPendingForRecipient(owner, 100).collectList().block()).isEmpty();
        var suppressed = direct.deliverDirect(new auto.parto.dto.NotificationRequest("safe-4", owner.toString(), request.type(), "safe", 4L)).block();
        assertThat(suppressed.suppressed()).isTrue();
        assertThat(suppressed.delivered()).isFalse();
        org.mockito.Mockito.verify(sse, org.mockito.Mockito.times(2)).sendViaSSE(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(repository.claimNotification(queued.publicId(), "new-worker", Instant.now(), Duration.ofSeconds(30)).block()).isNull();
        // A failed lookup is neither consent nor suppression. The same durable record is retried boundedly.
        var unavailable = org.mockito.Mockito.mock(auto.parto.service.notification.NotificationPreferenceFilter.class);
        org.mockito.Mockito.when(unavailable.canSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("unavailable")));
        var failingStore = new auto.parto.service.notification.HybridNotificationStore(redis, provider, new com.fasterxml.jackson.databind.ObjectMapper(), unavailable);
        var pending = new auto.parto.service.notification.ResilientNotificationService(breaker, sse, failingStore)
                .deliverDirect(new auto.parto.dto.NotificationRequest("safe-5", owner.toString(), request.type(), "safe", 5L)).block();
        assertThat(pending.persisted()).isTrue();
        assertThat(pending.delivered()).isFalse();
        assertThat(pending.suppressed()).isFalse();
        assertThat(repository.findPendingForRecipient(owner, 100).collectList().block()).extracting(NotificationRecord::publicId).containsExactly(pending.publicId());
        var retryWorker = new auto.parto.service.notification.ResilientPendingNotificationService(failingStore, sse, breaker);
        for (int attempt = 0; attempt < 2; attempt++) {
            databaseClient.sql("UPDATE notifications SET next_attempt_at = CURRENT_TIMESTAMP WHERE public_id = :id")
                    .bind("id", pending.publicId()).fetch().rowsUpdated().block();
            assertThat(retryWorker.deliverPendingOnConnect(owner.toString()).block()).isZero();
        }
        assertThat(databaseClient.sql("SELECT status FROM notifications WHERE public_id = :id")
                .bind("id", pending.publicId()).map((r,m) -> r.get("status", String.class)).one().block()).isEqualTo("EXHAUSTED");
        org.mockito.Mockito.verify(sse, org.mockito.Mockito.times(2)).sendViaSSE(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void delayedLookupCannotSendAfterAnotherRecoveryReclaimsItsLease(boolean sameServiceInstance) throws Exception {
        UUID owner = UUID.randomUUID();
        insertUser(owner).block();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var firstRequest = new auto.parto.dto.NotificationRequest("lease-first", owner.toString().toUpperCase(java.util.Locale.ROOT),
                auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        var secondRequest = new auto.parto.dto.NotificationRequest("lease-second", owner.toString(), firstRequest.type(), "safe", 2L);
        var firstRow = repository.enqueue(owner, firstRequest.type().name(), mapper.writeValueAsString(firstRequest)).block();
        var secondRow = repository.enqueue(owner, secondRequest.type().name(), mapper.writeValueAsString(secondRequest)).block();
        var redis = org.mockito.Mockito.mock(auto.parto.service.notification.RedisNotificationStore.class);
        org.mockito.Mockito.when(redis.replacePendingNotifications(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.when(redis.removeNotification(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just(1L));
        var provider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(repository);
        var policy = org.mockito.Mockito.mock(auto.parto.service.notification.NotificationPreferenceFilter.class);
        var lookupStarted = reactor.core.publisher.Sinks.<Void>one();
        var delayedLookup = reactor.core.publisher.Sinks.<Boolean>one();
        var replacementLookup = reactor.core.publisher.Sinks.<Boolean>one();
        var replacementStarted = reactor.core.publisher.Sinks.<Void>one();
        var firstFinished = reactor.core.publisher.Sinks.<Integer>one();
        var lookups = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.when(policy.canSend(owner.toString(), "push", firstRequest.type().name()))
                .thenAnswer(ignored -> {
                    int attempt = lookups.getAndIncrement();
                    if (attempt == 0) {
                        lookupStarted.tryEmitEmpty();
                        return delayedLookup.asMono();
                    }
                    if (attempt == 1) {
                        replacementStarted.tryEmitEmpty();
                        return replacementLookup.asMono();
                    }
                    return Mono.just(true);
                });
        var store = new auto.parto.service.notification.HybridNotificationStore(redis, provider, mapper, policy);
        var sse = org.mockito.Mockito.mock(auto.parto.service.notification.SSENotificationService.class);
        var written = new java.util.concurrent.ConcurrentLinkedQueue<UUID>();
        org.mockito.Mockito.when(sse.sendViaSSE(org.mockito.ArgumentMatchers.eq(owner.toString()),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> { written.add(call.getArgument(1)); return true; });
        var breaker = org.mockito.Mockito.mock(auto.parto.service.BasicCircuitBreaker.class);
        var workerA = new auto.parto.service.notification.ResilientPendingNotificationService(store, sse, breaker);
        var workerB = sameServiceInstance ? workerA
                : new auto.parto.service.notification.ResilientPendingNotificationService(store, sse, breaker);
        Mono<Integer> reclaim = lookupStarted.asMono()
                // Deterministically cross the lease boundary while A's lookup is unresolved.
                .then(databaseClient.sql("UPDATE notifications SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE recipient_user_id = (SELECT id FROM app_users WHERE public_id = :owner) AND lease_owner IS NOT NULL")
                        .bind("owner", owner).fetch().rowsUpdated())
                .then(Mono.zip(Mono.defer(() -> workerB.deliverPendingOnConnect(owner.toString())),
                        replacementStarted.asMono().doOnSuccess(ignored -> delayedLookup.tryEmitValue(true))
                                .then(firstFinished.asMono())
                                .flatMap(ignored -> databaseClient.sql("SELECT status FROM notifications WHERE public_id = :id")
                                        .bind("id", firstRow.publicId()).map((row, metadata) -> row.get("status", String.class)).one())
                                .doOnNext(status -> {
                                    assertThat(status).isEqualTo("PENDING");
                                    assertThat(written).doesNotContain(firstRow.publicId());
                                    replacementLookup.tryEmitValue(true);
                                })))
                .map(tuple -> tuple.getT1());
        StepVerifier.create(Mono.zip(workerA.deliverPendingOnConnect(owner.toString())
                .doOnSuccess(firstFinished::tryEmitValue), reclaim))
                .assertNext(counts -> assertThat(counts.getT1() + counts.getT2()).isEqualTo(2))
                .expectComplete().verify(Duration.ofSeconds(10));
        assertThat(written).containsExactlyInAnyOrder(firstRow.publicId(), secondRow.publicId());
        assertThat(repository.findPendingForRecipient(owner, 100).collectList().block()).isEmpty();
    }

    @Test
    void renewalCannotReviveExpiredOrReplacedClaimAndRequiresPendingStatus() {
        UUID owner = UUID.randomUUID();
        insertUser(owner).block();
        var row = repository.enqueue(owner, "NEW_SEARCH_POSTED", "{}").block();
        repository.claimNotification(row.publicId(), "claim-a", Instant.now(), Duration.ofSeconds(30)).block();
        assertThat(repository.renewClaim(row.publicId(), "other", Duration.ofSeconds(30)).block()).isFalse();
        assertThat(repository.renewClaim(row.publicId(), "claim-a", Duration.ofSeconds(30)).block()).isTrue();
        databaseClient.sql("UPDATE notifications SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE public_id = :id")
                .bind("id", row.publicId()).fetch().rowsUpdated().block();
        assertThat(repository.renewClaim(row.publicId(), "claim-a", Duration.ofSeconds(30)).block()).isFalse();
        repository.claimNotification(row.publicId(), "claim-b", Instant.now(), Duration.ofSeconds(30)).block();
        assertThat(repository.renewClaim(row.publicId(), "claim-a", Duration.ofSeconds(30)).block()).isFalse();
        assertThat(repository.acknowledgeDelivery(row.publicId(), "claim-b").block()).isTrue();
        assertThat(repository.renewClaim(row.publicId(), "claim-b", Duration.ofSeconds(30)).block()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null", "abbreviated"})
    void malformedFirstClaimDoesNotHideFollowingValidNotificationInSamePass(String invalidForm) throws Exception {
        UUID owner = UUID.fromString(UUID.randomUUID().toString().substring(0, 24) + "000000000001");
        insertUser(owner).block();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String invalidOwner = invalidForm.equals("null") ? null : owner.toString().substring(0, 24) + "1";
        var invalid = new auto.parto.dto.NotificationRequest("invalid", invalidOwner, auto.parto.enums.NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        var valid = new auto.parto.dto.NotificationRequest("valid", owner.toString(), invalid.type(), "safe", 2L);
        var invalidRow = repository.enqueue(owner, invalid.type().name(), mapper.writeValueAsString(invalid)).block();
        var uppercaseValid = new auto.parto.dto.NotificationRequest(valid.id(), owner.toString().toUpperCase(java.util.Locale.ROOT), valid.type(), valid.message(), valid.sysTime());
        var validRow = repository.enqueue(owner, valid.type().name(), mapper.writeValueAsString(uppercaseValid)).block();
        var redis = org.mockito.Mockito.mock(auto.parto.service.notification.RedisNotificationStore.class);
        org.mockito.Mockito.when(redis.replacePendingNotifications(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
        org.mockito.Mockito.when(redis.removeNotification(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just(1L));
        var provider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(repository);
        var preferences = org.mockito.Mockito.mock(auto.parto.service.notification.NotificationPreferenceFilter.class);
        org.mockito.Mockito.when(preferences.canSend(owner.toString(), "push", valid.type().name())).thenReturn(Mono.just(true));
        var store = new auto.parto.service.notification.HybridNotificationStore(redis, provider, mapper, preferences);
        var sse = org.mockito.Mockito.mock(auto.parto.service.notification.SSENotificationService.class);
        org.mockito.Mockito.when(sse.sendViaSSE(owner.toString(), validRow.publicId(), valid)).thenReturn(true);
        var worker = new auto.parto.service.notification.ResilientPendingNotificationService(store, sse,
                org.mockito.Mockito.mock(auto.parto.service.BasicCircuitBreaker.class));
        StepVerifier.create(worker.deliverPendingOnConnect(owner.toString())).expectNext(1).expectComplete().verify(Duration.ofSeconds(5));
        assertThat(databaseClient.sql("SELECT status FROM notifications WHERE public_id = :id").bind("id", invalidRow.publicId())
                .map((row, metadata) -> row.get("status", String.class)).one().block()).isEqualTo("SUPPRESSED");
        assertThat(databaseClient.sql("SELECT last_failure_code FROM notifications WHERE public_id = :id").bind("id", invalidRow.publicId())
                .map((row, metadata) -> row.get("last_failure_code", String.class)).one().block()).isEqualTo("INVALID_NOTIFICATION");
        assertThat(repository.claimNotification(invalidRow.publicId(), "another-worker", Instant.now(), Duration.ofSeconds(30)).block()).isNull();
        org.mockito.Mockito.verify(sse).sendViaSSE(owner.toString(), validRow.publicId(), valid);
        org.mockito.Mockito.verify(sse, org.mockito.Mockito.never()).sendViaSSE(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(invalidRow.publicId()), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(preferences, org.mockito.Mockito.times(1)).canSend(owner.toString(), "push", valid.type().name());
    }

    private reactor.core.publisher.Mono<Void> insertUser(UUID publicId) {
        return databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'BUYER', 'ACTIVE')
                        """)
                .bind("publicId", publicId).bind("email", "notification-" + publicId + "@example.test")
                .fetch().rowsUpdated().then();
    }

    private reactor.core.publisher.Mono<Void> createFailingOutboxTrigger() {
        return databaseClient.sql("""
                        CREATE FUNCTION test_fail_notification_outbox() RETURNS trigger
                        LANGUAGE plpgsql AS $$
                        BEGIN
                            RAISE EXCEPTION 'forced notification outbox failure';
                        END;
                        $$;
                        """).fetch().rowsUpdated().then(databaseClient.sql("""
                        CREATE TRIGGER test_fail_notification_outbox_trigger
                        BEFORE INSERT ON outbox_events
                        FOR EACH ROW WHEN (NEW.event_type = 'notification.requested')
                        EXECUTE FUNCTION test_fail_notification_outbox();
                        """).fetch().rowsUpdated().then()).then();
    }

    private reactor.core.publisher.Mono<Void> dropFailingOutboxTrigger() {
        return databaseClient.sql("DROP TRIGGER test_fail_notification_outbox_trigger ON outbox_events")
                .fetch().rowsUpdated()
                .then(databaseClient.sql("DROP FUNCTION test_fail_notification_outbox()")
                        .fetch().rowsUpdated())
                .then();
    }

    private long countNotifications(UUID owner, String eventType) {
        return databaseClient.sql("""
                        SELECT count(*) AS total
                        FROM notifications notification
                        JOIN app_users owner ON owner.id = notification.recipient_user_id
                        WHERE owner.public_id = :owner AND notification.event_type = :eventType
                        """)
                .bind("owner", owner)
                .bind("eventType", eventType)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block();
    }

    private long countOutboxEvents(String eventType, String notificationEventType) {
        return databaseClient.sql("""
                        SELECT count(*) AS total
                        FROM outbox_events event
                        WHERE event.event_type = :eventType
                          AND event.payload ->> 'notificationType' = :notificationEventType
                        """)
                .bind("eventType", eventType)
                .bind("notificationEventType", notificationEventType)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block();
    }

    private record OutboxSnapshot(String eventType, Integer schemaVersion, UUID aggregatePublicId,
                                  String payload, Long deliveryCount) {
    }
}
