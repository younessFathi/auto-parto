# AP-0002 Introduce Reactive PostgreSQL Persistence

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Platform and Data](../features/F01-platform-and-data.md)
- **Linked Tickets:** [AP-0001](AP-0001-postgresql-schema.md)
- **Source:** `architecture.md` Request Flow and PostgreSQL Design

## Description
Implement persistence boundaries with R2DBC, bounded queries, explicit empty results, and reactive transactions.

## Initial Implementation Slice

The first slice adds the PostgreSQL R2DBC driver, pooled connection configuration, a reactive transaction manager/operator, and a real R2DBC adapter for the AP-0001 audit outbox delivery port. The completed migration uses PostgreSQL as the only application persistence runtime; feature repositories remain behind their existing service contracts with no dual source of truth.

## Business Persistence Slice: Application Users

The first business slice adds a conditional R2DBC `AppUserRepository` for the versioned `app_users` schema. It uses public UUIDs at the repository boundary, keeps internal numeric IDs and audit-reference columns private, returns explicit empty results for missing identities, and creates users through `TransactionalOperator` so the database audit trigger and user insert share one transaction. A second slice adds the opt-in PostgreSQL seller email-OTP path: seller identity/profile/challenge creation is transactional, OTPs are hashed and atomically consumed, failed attempts are bounded, and bounds failed attempts. Buyer identity and the remaining business aggregates are now covered by the completed PostgreSQL cutover.

## Follow-up: Buyer Identity and Profile Cutover

Before enabling a PostgreSQL buyer-profile HTTP endpoint, implement a PostgreSQL buyer onboarding/identity flow that atomically creates the `app_users` and `buyer_profiles` rows and issues a canonical JWT whose principal ID is the user's public UUID. Until then, keep the endpoint disabled behind a dedicated buyer-PostgreSQL identity flag. Cover the real credential-to-profile-update path end to end; repository tests that insert a profile directly do not verify this contract.

Buyer identity enablement must also reliably enable the required general R2DBC infrastructure: document `APP_POSTGRES_R2DBC_ENABLED=true` as required for either identity path, or bind the parent setting to an explicit logical OR of seller and buyer identity flags. Test the configuration where seller identity is explicitly disabled and buyer identity is enabled. Add buyer Testcontainers coverage for replacement invalidation, lockout, exactly-once consumption, concurrent replacement, and transactional onboarding rollback.

For onboarding rollback coverage, induce a failure after `app_users` has been inserted (for example, a buyer-profile field exceeding its database column length) and assert that no application user, buyer profile, or OTP challenge remains. A duplicate-email assertion alone fails at the first insert and cannot prove rollback of dependent writes.

**Resolution (2026-09-25):** `failedBuyerProfileInsertRollsBackUserProfileAndOtp` now induces an oversized buyer-profile first name after the user insert and proves that the reactive transaction leaves no user, profile, or OTP challenge. The duplicate-email test is retained as separate duplicate-rejection coverage.

## Combined AP-0002/AP-0003 Progress

The PostgreSQL transaction infrastructure now activates when buyer identity is enabled, even if seller identity and the general PostgreSQL flag are disabled. This prevents the buyer-only configuration from exposing profile/identity beans without the shared R2DBC transaction infrastructure. The configuration boundary is covered by `PostgresqlPersistenceConfigurationTest`. Active identity, marketplace, wallet, device-token, notification, file-metadata, notification-preferences, and object-storage request paths now use PostgreSQL under their cutover flags; provider-subject identity lookup, OTP operations, and profile/status transitions are covered as well. The PostgreSQL-only runtime suppresses unsupported legacy contracts and fails closed on them. A PostgreSQL-only Compose application profile now starts without MongoDB, uses ordered migrations and fixtures, exposes an unauthenticated health check, and reports PostgreSQL and Redis readiness. Full verification passed 296 unit tests, 113 integration tests, and JaCoCo checks.

The first marketplace persistence boundaries are now available as opt-in R2DBC repositories for part requests, part listings, and offers. They use canonical public UUIDs, bounded owner queries, explicit empty results, transaction-safe creation, cross-aggregate foreign-key enforcement, and optimistic version checks for status updates. `PieceService` now routes canonical UUID request reads, existence checks, creation, and image updates through `PartRequestRepository` when the marketplace flag is enabled, with bounded canonical UUID coverage. Buyer identity checks, reads, activation, and deactivation now route through the PostgreSQL user/profile boundary when buyer identity is enabled. Seller identity reads/deactivation and seller wallet mutations likewise route through PostgreSQL; wallet mutations require explicit currency and an idempotency key. Authenticated offer endpoints now derive the counterparty from the request/listing owner row and route creation, actor-scoped reads, and optimistic status updates through PostgreSQL. Canonical image updates validate UUID file IDs and return the PostgreSQL request; upload transactions establish file ownership.

The wallet boundary is now available behind `APP_POSTGRES_WALLET_ENABLED`. It stores balances as `long` minor units, serializes mutations with row locks, uses overflow-safe arithmetic, persists immutable audited ledger entries, and makes retries idempotent through wallet-scoped keys. The seller wallet API now uses the public-ID contract and requires an `Idempotency-Key` for PostgreSQL mutations.

The device-token boundary is now available behind `APP_POSTGRES_DEVICE_TOKENS_ENABLED`. Registration and invalidation are audited in PostgreSQL, active-token reads use bounded owner queries, and Redis is retained as the delivery projection. The notification service uses PostgreSQL as the source of truth when this boundary is enabled and falls back to its existing Redis-only behavior when disabled.

The notification boundary is now available behind `APP_POSTGRES_NOTIFICATIONS_ENABLED`. It persists JSON payloads, supports bounded recipient-scoped pending reads, makes status transitions idempotent, records channel delivery attempts, and marks a recipient's pending records dispatched transactionally when the delivery projection is cleared. `HybridNotificationStore` now reads and clears PostgreSQL records when this boundary is enabled, while Redis remains the delivery projection.

File metadata is now available behind `APP_POSTGRES_FILES_ENABLED` with owner/target foreign-key enforcement, owner-scoped target creation, bounded target reads, audited pending-to-active finalization, and audited tombstoning. Canonical UUID request-image reads, authenticated buyer request-image uploads, and authenticated seller listing-image uploads now use the PostgreSQL file boundary and the provider-neutral `FileStorage` port; uploads are bounded by configuration and validated against an image MIME allowlist and content signature before staging, while staged objects are deleted and metadata is tombstoned when finalization fails. Local development uses the filesystem adapter, while the S3-compatible adapter is required for production and rejects missing credentials or unbounded concurrency. The PostgreSQL-only runtime fails closed on unsupported contracts. Notification preferences are available behind `APP_POSTGRES_NOTIFICATION_PREFERENCES_ENABLED` with canonical user ownership and transactional upserts. The PostgreSQL-only runtime suppresses unsupported legacy contracts and fails closed on them. Active file metadata is counted before its bounded object-existence scan so reconciliation fails closed when the configured capacity is exceeded; the asynchronous health gate keeps readiness unhealthy until reconciliation succeeds.

## Acceptance Criteria
1. Request-path database access uses an approved non-blocking PostgreSQL client.
2. SQL and database details remain behind repository boundaries.
3. Queries are bounded and missing records are represented explicitly.
4. Multi-record mutations use reactive transactions and roll back atomically.
5. Domain relationships use composition unless implementations are genuinely substitutable through a stable contract.

## Test Scenarios
1. **Given** an existing record, **when** queried, **then** it is returned reactively without blocking.
2. **Given** a missing record, **when** queried, **then** an explicit empty/not-found result occurs.
3. **Given** a failed multi-record mutation, **when** the transaction ends, **then** all changes roll back.
4. **Given** a persistence or provider design, **when** architecture rules are checked, **then** inheritance is used only for valid substitutable behavior.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, configuration and documentation are complete, and all applicable test and verification suites pass.
