# AP-0037 Remove MongoDB Runtime and Compatibility Code

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** PostgreSQL-only runtime cleanup
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md), [AP-0003](AP-0003-development-cutover.md), [AP-0040](AP-0040-remove-dead-runtime-state.md)

## Goal

Make PostgreSQL the only application persistence runtime. The project is still in development and has no MongoDB production data to preserve, so the completed and verified PostgreSQL boundaries can replace the remaining MongoDB compatibility paths.

## Scope

1. Confirm every active controller and service path uses a PostgreSQL repository boundary.
2. Remove MongoDB fallback branches, including the obsolete Mongo-only offer path (`PieceOfferService` and its repository/model support), together with repository interfaces and implementations, MongoDB models, mappers, configuration, dependencies, fixtures, and tests that no longer have callers.
3. Remove MongoDB from Compose, application configuration, CI, and active documentation.
4. Preserve audit, notification, storage, authorization, ownership, retry, and readiness behavior during the removal.
5. Keep PostgreSQL/Testcontainers coverage for each removed boundary and add a startup test proving the application no longer creates MongoDB infrastructure.

## Acceptance Criteria

- The application starts and serves all supported contracts with PostgreSQL and Redis only.
- No production application code imports Spring Data MongoDB or uses `@Document`.
- No service silently falls back to MongoDB when a PostgreSQL boundary is unavailable.
- The obsolete Mongo-only offer path is removed; active offer behavior uses the PostgreSQL `OfferRepository` boundary.
- MongoDB dependencies, configuration, Compose services, and active documentation are removed.
- Focused unit and integration coverage protects each migrated path and dependency-failure behavior.
- `git diff --check`, `./mvnw -B clean test`, and `./mvnw -B verify` pass with Docker available.
- A sanitized `.agent-trace/` record is added for each implementation session.

## Implementation Tracking

- Started on branch `feat/ap-0037-remove-mongodb` from the latest GitHub `main`.
- Initial inventory found MongoDB models and repositories still present as compatibility paths, while PostgreSQL repositories cover identity, marketplace, wallets, device tokens, notifications, files, notification preferences, and object storage.
- Removed the unused `CustomerJwt` and `PushDeliveryLog` Mongo document classes after confirming they have no production or test callers. Remaining Mongo models stay until their active compatibility callers are removed.
- The next bounded cleanup slice includes the obsolete Mongo-only offer path; PostgreSQL offer persistence and its API contract remain in scope for regression verification.
- Removed the obsolete Mongo-only offer path (`PieceOfferService`, `IPieceOfferService`, `PieceOfferDbMapper`, `PieceOffer`, `IPieceOfferRepository`, and `PieceOfferRepository`) and its Mongo-specific tests. The active PostgreSQL `OfferRepository` path remains unchanged.
- Removed the remaining Mongo-backed compatibility services, repositories, models, mappers, authentication strategies, fixtures, and tests. Buyer, seller, piece, file, and notification-preference services now use only their PostgreSQL boundaries and canonical UUID identifiers.
- Removed the MongoDB starter and Testcontainers module, Mongo configuration and Compose service, Mongo environment examples, and the obsolete Atlas document. Active architecture and cutover documentation now describes PostgreSQL and Redis as the runtime persistence dependencies.
- Updated deployment and integration test configuration for PostgreSQL-only startup. The storage reconciliation service and readiness gate now require both cutover and storage-reconciliation flags, preventing an orphaned gate when cutover is disabled.
- Added a test-profile OTP pepper so all PostgreSQL identity repositories can be constructed in application-level integration tests without any secret material.
- Verification completed on 2026-10-23: `git diff --check`; focused configuration and image tests (21 tests); `./mvnw -B test` (336 tests, 0 failures, 1 intentional skip); and Docker-backed `./mvnw -B verify` (134 integration tests, 0 failures, 1 intentional skip, JaCoCo checks passed).
- Acceptance is complete: production code has no Spring Data MongoDB dependency, `@Document` model, or Mongo fallback path; Compose starts with PostgreSQL and Redis only; PostgreSQL repository and deployment integration tests pass.
- Completion review follow-up: removed the obsolete always-enabled `springboot-app` Compose service. `springboot-postgresql-app` is now the single application path, activated with the `postgres` profile and ordered after PostgreSQL migration and fixtures; `DeploymentComposeIT` starts and probes that same profile-backed service.
