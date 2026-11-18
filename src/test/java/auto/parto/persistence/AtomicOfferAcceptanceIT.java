package auto.parto.persistence;

import auto.parto.dto.UpdateOfferStatusRequest;
import auto.parto.service.PostgresqlOfferService;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AtomicOfferAcceptanceIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private DatabaseClient db;
    private TransactionalOperator transactions;
    private R2dbcOfferRepository repository;
    private PostgresqlOfferService service;

    @BeforeAll
    void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        ConnectionFactory cf = io.r2dbc.spi.ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql").option(HOST, POSTGRES.getHost()).option(PORT, POSTGRES.getFirstMappedPort())
                .option(USER, POSTGRES.getUsername()).option(PASSWORD, POSTGRES.getPassword())
                .option(DATABASE, POSTGRES.getDatabaseName()).build());
        db = DatabaseClient.create(cf);
        transactions = TransactionalOperator.create(new R2dbcTransactionManager(cf));
        repository = new R2dbcOfferRepository(db, transactions);
        service = new PostgresqlOfferService(provider(repository));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void commitsBothOriginJourneysWithTwoRecoverableIntentsAndNoMoneyMovement(boolean request) {
        Fixture f = fixture(request);
        OfferRecord competing = create(f).block();
        var wallets = new R2dbcWalletRepository(db, transactions);
        wallets.mutate(f.seller, "MAD", 500, "CREDIT", "seed-" + f.target, null).block();
        var before = wallets.findByOwnerPublicId(f.seller, "MAD").block();
        long ledgerBefore = scalar("SELECT count(*) AS n FROM wallet_ledger");
        OfferRecord result = accept(f).block();
        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.acceptancePublicId()).isNotNull();
        assertThat(targetStatus(f)).isEqualTo(request ? "CLOSED" : "RESERVED");
        assertThat(repository.findByPublicId(competing.publicId()).block().status()).isEqualTo("REJECTED");
        assertThat(repository.findByPublicId(competing.publicId()).block().version()).isEqualTo(1);
        assertIntents(f, 2);
        assertThat(wallets.findByOwnerPublicId(f.seller, "MAD").block()).isEqualTo(before);
        assertThat(scalar("SELECT count(*) AS n FROM wallet_ledger")).isEqualTo(ledgerBefore);
        var notifications = new R2dbcNotificationRepository(db, transactions);
        for (UUID owner : new UUID[]{f.buyer, f.seller}) {
            var pending = notifications.findPendingForRecipient(owner, 100).collectList().block();
            assertThat(pending).hasSize(1);
            assertThat(pending.getFirst().eventType()).isEqualTo("OFFER_ACCEPTED");
            assertThat(pending.getFirst().payloadJson()).contains(f.offer.publicId().toString());
        }
    }

    @Test
    void repeatedAndConcurrentAcceptanceAfterAdapterRestartUsesOneImmutableReceipt() {
        Fixture f = fixture(false);
        OfferRecord accepted = accept(f).block();
        var restarted = new R2dbcOfferRepository(db, transactions);
        var repeats = Flux.range(0, 5).flatMap(i -> restarted.updateStatus(f.offer.publicId(), f.recipient(),
                "ACCEPTED", i % 2, Instant.now()), 5).collectList().block();
        assertThat(repeats).hasSize(5).allSatisfy(offer -> assertThat(offer).isEqualTo(accepted));
        assertIntents(f, 2);
        assertThat(scalar("SELECT version AS n FROM part_listings WHERE public_id = '" + f.target + "'" )).isEqualTo(1);
        assertThatThrownBy(() -> restarted.updateStatus(f.offer.publicId(), f.recipient(), "ACCEPTED", 99, Instant.now()).block())
                .hasMessageContaining("409");
        assertThatThrownBy(() -> db.sql("DELETE FROM offer_acceptances WHERE public_id = :id")
                .bind("id", accepted.acceptancePublicId()).fetch().rowsUpdated().block()).hasMessageContaining("immutable");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void competingAcceptsHaveOneWinnerAndOneRejectedCompetitor(boolean request) {
        Fixture f = fixture(request);
        OfferRecord competing = create(f).block();
        var outcomes = Flux.merge(outcome(accept(f)), outcome(repository.updateStatus(competing.publicId(),
                f.recipient(), "ACCEPTED", 0, Instant.now()))).collectList().block();
        assertThat(outcomes).containsExactlyInAnyOrder("ACCEPTED", "CONFLICT");
        var records = Flux.just(f.offer.publicId(), competing.publicId()).concatMap(repository::findByPublicId).collectList().block();
        assertThat(records).extracting(OfferRecord::status).containsExactlyInAnyOrder("ACCEPTED", "REJECTED");
        assertThat(records.stream().filter(o -> o.acceptancePublicId() != null).count()).isEqualTo(1);
        assertThat(scalar("SELECT count(*) AS n FROM notifications WHERE recipient_user_id IN (SELECT id FROM app_users WHERE public_id IN ('" + f.buyer + "','" + f.seller + "'))")).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void offerCreationRacingAcceptanceCannotLeavePendingOfferOnUnavailableTarget(boolean request) {
        Fixture f = fixture(request);
        var outcomes = Flux.merge(outcome(accept(f)), outcome(create(f))).collectList().block();
        assertThat(outcomes).contains("ACCEPTED");
        assertThat(countPending(f)).isZero();
        assertThatThrownBy(() -> create(f).block()).hasMessageContaining("409");
        assertThat(scalar("SELECT count(*) AS n FROM offer_acceptances WHERE offer_id = (SELECT id FROM offers WHERE public_id = '" + f.offer.publicId() + "')")).isEqualTo(1);
    }

    @Test
    void cancellationRacingAcceptanceNeverLeavesPartialEffects() {
        for (int i = 0; i < 4; i++) {
            Fixture f = fixture(false);
            var outcomes = Flux.merge(
                    service.updateStatus(f.offer.publicId(), f.recipient(), new UpdateOfferStatusRequest("ACCEPTED", 0L))
                            .map(o -> o.status()).onErrorResume(e -> conflictOutcome(e)),
                    service.updateStatus(f.offer.publicId(), f.buyer, new UpdateOfferStatusRequest("CANCELLED", 0L))
                            .map(o -> o.status()).onErrorResume(e -> conflictOutcome(e))).collectList().block();
            assertThat(outcomes).contains("CONFLICT");
            String status = repository.findByPublicId(f.offer.publicId()).block().status();
            assertThat(status).isIn("ACCEPTED", "CANCELLED");
            assertIntents(f, "ACCEPTED".equals(status) ? 2 : 0);
            assertThat(targetStatus(f)).isEqualTo("ACCEPTED".equals(status) ? "RESERVED" : "PUBLISHED");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void manualTargetDecisionRacingAcceptanceSharesRowAndVersionProtection(boolean request) {
        Fixture f = fixture(request);
        Mono<Boolean> manual = request
                ? new R2dbcPartRequestRepository(db, transactions).transition(f.target, f.buyer, "CANCELLED").hasElement()
                : new R2dbcPartListingRepository(db, transactions).updateStatus(f.target, f.seller, "RESERVED", 0, Instant.now()).hasElement();
        var outcomes = Flux.merge(outcome(accept(f)), manual.map(done -> done ? "MANUAL" : "CONFLICT")).collectList().block();
        assertThat(outcomes.stream().filter("CONFLICT"::equals).count()).isEqualTo(1);
        boolean accepted = "ACCEPTED".equals(repository.findByPublicId(f.offer.publicId()).block().status());
        assertIntents(f, accepted ? 2 : 0);
        assertThat(targetStatus(f)).isEqualTo(request ? (accepted ? "CLOSED" : "CANCELLED") : "RESERVED");
    }

    @ParameterizedTest @ValueSource(strings = {"part_listings", "offers", "competitors", "notifications", "outbox_events", "offer_acceptances"})
    void failureAtEveryRequiredDatabaseBoundaryRollsBackAndRetryCompletesOnce(String stage) {
        Fixture f = fixture(false);
        OfferRecord competitor = create(f).block();
        String table = stage.equals("competitors") ? "offers" : stage;
        String operation = table.equals("part_listings") || table.equals("offers") ? "UPDATE" : "INSERT";
        String when = stage.equals("offers") ? "WHEN (NEW.status = 'ACCEPTED')"
                : stage.equals("competitors") ? "WHEN (NEW.status = 'REJECTED')" : "";
        db.sql("""
                CREATE FUNCTION test_acceptance_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF TG_TABLE_NAME = 'notifications' AND NOT EXISTS (
                        SELECT 1 FROM notifications WHERE payload ->> 'id' = '%s-%s') THEN
                        RETURN NEW;
                    END IF;
                    RAISE EXCEPTION 'forced acceptance boundary failure';
                END; $$
                """.formatted(f.offer.publicId(), f.buyer)).fetch().rowsUpdated().block();
        db.sql("CREATE TRIGGER test_acceptance_failure_trigger BEFORE " + operation + " ON " + table
                + " FOR EACH ROW " + when + " EXECUTE FUNCTION test_acceptance_failure()")
                .fetch().rowsUpdated().block();
        try {
            assertThatThrownBy(() -> accept(f).block()).hasMessageContaining("forced acceptance boundary failure");
            assertThat(repository.findByPublicId(f.offer.publicId()).block().status()).isEqualTo("PENDING");
            assertThat(repository.findByPublicId(competitor.publicId()).block().status()).isEqualTo("PENDING");
            assertThat(targetStatus(f)).isEqualTo("PUBLISHED");
            assertIntents(f, 0);
        } finally {
            db.sql("DROP TRIGGER test_acceptance_failure_trigger ON " + table).fetch().rowsUpdated().block();
            db.sql("DROP FUNCTION test_acceptance_failure()").fetch().rowsUpdated().block();
        }
        assertThat(accept(f).block().acceptancePublicId()).isNotNull();
        accept(f).block();
        assertIntents(f, 2);
    }

    @Test
    void legacyAcceptedWithoutReceiptConflictsAndCannotClaimNewEffectsOrBypassUniqueTarget() {
        Fixture f = fixture(false);
        OfferRecord second = create(f).block();
        db.sql("UPDATE offers SET status = 'ACCEPTED' WHERE public_id = :id").bind("id", f.offer.publicId())
                .fetch().rowsUpdated().block();
        assertThatThrownBy(() -> accept(f).block()).hasMessageContaining("receipt unavailable");
        assertThatThrownBy(() -> db.sql("UPDATE offers SET status = 'ACCEPTED' WHERE public_id = :id")
                .bind("id", second.publicId()).fetch().rowsUpdated().block()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertIntents(f, 0);
        assertThat(targetStatus(f)).isEqualTo("PUBLISHED");
        assertThat(repository.findByPublicId(f.offer.publicId()).block().acceptancePublicId()).isNull();
    }

    @Test
    void committedAcceptanceRecoversAfterRedisAndTransportOutageAndHonorsOrderUpdateOptOut() {
        Fixture f = fixture(false);
        var preferences = new R2dbcNotificationPreferencesRepository(db, transactions);
        preferences.save(f.buyer, new NotificationPreferencesRecord(null, f.buyer, true, true, false,
                true, true, true, false, false, null, null)).block();
        OfferRecord accepted = accept(f).block();
        var redis = org.mockito.Mockito.mock(auto.parto.service.notification.RedisNotificationStore.class);
        org.mockito.Mockito.when(redis.replacePendingNotifications(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
        org.mockito.Mockito.when(redis.removeNotification(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just(0L));
        NotificationRepository notifications = new R2dbcNotificationRepository(db, transactions);
        var provider = new ObjectProvider<NotificationRepository>() {
            public NotificationRepository getObject(Object... args) { return notifications; }
            public NotificationRepository getIfAvailable() { return notifications; }
            public NotificationRepository getIfUnique() { return notifications; }
        };
        var store = new auto.parto.service.notification.HybridNotificationStore(redis, provider,
                new com.fasterxml.jackson.databind.ObjectMapper(), new auto.parto.service.notification.NotificationPreferenceFilter(
                        new auto.parto.service.NotificationPreferencesService(preferences)));
        var sse = org.mockito.Mockito.mock(auto.parto.service.notification.SSENotificationService.class);
        var worker = new auto.parto.service.notification.ResilientPendingNotificationService(store, sse,
                org.mockito.Mockito.mock(auto.parto.service.BasicCircuitBreaker.class));
        assertThatThrownBy(() -> worker.deliverPendingOnConnect(f.buyer.toString()).block()).hasMessageContaining("redis unavailable");
        assertThat(repository.findByPublicId(f.offer.publicId()).block()).isEqualTo(accepted);
        assertThat(notifications.findPendingForRecipient(f.buyer, 100).collectList().block()).hasSize(1);
        org.mockito.Mockito.when(redis.replacePendingNotifications(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.empty());
        assertThat(worker.deliverPendingOnConnect(f.buyer.toString()).block()).isZero();
        assertThat(worker.deliverPendingOnConnect(f.seller.toString()).block()).isZero();
        assertThat(notifications.findPendingForRecipient(f.buyer, 100).collectList().block()).isEmpty();
        assertThat(notifications.findPendingForRecipient(f.seller, 100).collectList().block()).hasSize(1);
        org.mockito.Mockito.when(sse.sendViaSSE(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        db.sql("UPDATE notifications SET next_attempt_at = CURRENT_TIMESTAMP WHERE payload ->> 'id' = :id")
                .bind("id", f.offer.publicId() + "-" + f.seller).fetch().rowsUpdated().block();
        var restarted = new auto.parto.service.notification.ResilientPendingNotificationService(store, sse,
                org.mockito.Mockito.mock(auto.parto.service.BasicCircuitBreaker.class));
        assertThat(restarted.deliverPendingOnConnect(f.seller.toString()).block()).isEqualTo(1);
        assertThat(repository.findByPublicId(f.offer.publicId()).block()).isEqualTo(accepted);
        assertIntents(f, 2);
        org.mockito.Mockito.verify(sse, org.mockito.Mockito.never()).sendViaSSE(org.mockito.ArgumentMatchers.eq(f.buyer.toString()),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private Mono<String> outcome(Mono<OfferRecord> work) {
        return work.map(OfferRecord::status).switchIfEmpty(Mono.just("CONFLICT")).onErrorResume(this::conflictOutcome);
    }
    private Mono<String> conflictOutcome(Throwable error) {
        if (error instanceof org.springframework.web.server.ResponseStatusException e && e.getStatusCode().value() == 409)
            return Mono.just("CONFLICT");
        return Mono.error(error);
    }
    private Mono<OfferRecord> accept(Fixture f) {
        return repository.updateStatus(f.offer.publicId(), f.recipient(), "ACCEPTED", 0, Instant.now());
    }
    private Mono<OfferRecord> create(Fixture f) {
        return f.request ? repository.createForRequest(f.seller, f.target, 1000, "MAD", false, 0, null)
                : repository.createForListing(f.buyer, f.target, 1000, "MAD", false, 0, null);
    }
    private Fixture fixture(boolean request) {
        UUID buyer = user("BUYER"), seller = user("SELLER"), target = UUID.randomUUID();
        if (request) db.sql("INSERT INTO part_requests (public_id, buyer_user_id, name) SELECT :id, id, 'safe' FROM app_users WHERE public_id = :owner")
                .bind("id", target).bind("owner", buyer).fetch().rowsUpdated().block();
        else db.sql("INSERT INTO part_listings (public_id, seller_user_id, name, amount_minor, currency, status, business_status) SELECT :id, id, 'safe', 1000, 'MAD', 'PUBLISHED', 'PUBLISHED' FROM app_users WHERE public_id = :owner")
                .bind("id", target).bind("owner", seller).fetch().rowsUpdated().block();
        Fixture empty = new Fixture(request, buyer, seller, target, null);
        return new Fixture(request, buyer, seller, target, create(empty).block());
    }
    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.sql("INSERT INTO app_users (public_id, email, role) VALUES (:id, :email, :role)")
                .bind("id", id).bind("email", id + "@example.test").bind("role", role).fetch().rowsUpdated().block();
        return id;
    }
    private String targetStatus(Fixture f) {
        return db.sql("SELECT status FROM " + (f.request ? "part_requests" : "part_listings") + " WHERE public_id = :id")
                .bind("id", f.target).map((r,m) -> r.get("status", String.class)).one().block();
    }
    private long countPending(Fixture f) {
        return scalar("SELECT count(*) AS n FROM offers WHERE " + (f.request ? "part_request_id" : "part_listing_id")
                + " = (SELECT id FROM " + (f.request ? "part_requests" : "part_listings") + " WHERE public_id = '" + f.target + "') AND status = 'PENDING'");
    }
    private void assertIntents(Fixture f, long expected) {
        assertThat(scalar("SELECT count(*) AS n FROM notifications WHERE payload ->> 'id' LIKE '" + f.offer.publicId() + "-%'" )).isEqualTo(expected);
        assertThat(scalar("SELECT count(*) AS n FROM outbox_events WHERE event_type = 'notification.requested' AND payload -> 'notificationPayload' ->> 'id' LIKE '" + f.offer.publicId() + "-%'" )).isEqualTo(expected);
        assertThat(scalar("SELECT count(*) AS n FROM offer_acceptances WHERE offer_id = (SELECT id FROM offers WHERE public_id = '" + f.offer.publicId() + "')")).isEqualTo(expected == 0 ? 0 : 1);
    }
    private long scalar(String sql) { return db.sql(sql).map((r,m) -> r.get("n", Long.class)).one().block(); }
    private record Fixture(boolean request, UUID buyer, UUID seller, UUID target, OfferRecord offer) {
        UUID recipient() { return request ? buyer : seller; }
    }
    private static ObjectProvider<OfferRepository> provider(OfferRepository repository) {
        return new ObjectProvider<>() {
            public OfferRepository getObject(Object... args) { return repository; }
            public OfferRepository getIfAvailable() { return repository; }
            public OfferRepository getIfUnique() { return repository; }
        };
    }
}
