# Current Architecture Risks

This document describes gaps observed in the current codebase. It is a review of the current implementation, not a statement that every item is already fixed or still open. Verify each item against the code before implementing a change.

## Critical Security Risks

### Credential exposure in documentation

`production-readiness/phase-1-security/US-001-revoke-exposed-secrets.md` contains real-looking credential values in its technical notes. A security remediation document must not reproduce credentials.

Actions:

- Revoke and rotate affected credentials.
- Remove values from documentation and Git history where applicable.
- Add secret scanning to CI.

### Inconsistent resource authorization

Several sensitive operations accept IDs from a path or request body. Authentication alone does not prove ownership.

Areas requiring explicit review include:

- Seller wallet operations
- Seller deactivation
- User-specific SSE subscriptions
- FCM device-token registration
- Notification delivery
- Image access and upload

Every operation should compare the requested resource owner with the authenticated principal, except for explicitly authorized administrators or system workflows.

### Multiple authentication approaches

The repository contains custom JWT, Google OAuth, and Clerk-related code. The Clerk filter contains placeholder issuer and key values and does not appear to be part of the active security chain.

This creates uncertainty about which authentication mechanism is authoritative. Unused or incomplete mechanisms should be removed or isolated behind a profile until fully configured and tested.

## Financial and Data Integrity Risks

### Money representation is inconsistent with the project standard

The project standard now specifies integer minor units, but the current wallet code uses `BigDecimal` in controller and repository signatures. This is not automatically incorrect, but it conflicts with the intended domain representation.

A migration must update:

- MongoDB models
- DTOs
- Controllers
- Services
- Repositories
- Mappers
- Tests
- API documentation

Use `long` minor units, currency metadata, overflow-safe arithmetic, atomic updates, and an auditable ledger.

### Wallet authorization and auditability

Wallet endpoints operate on seller IDs supplied by the caller. The implementation needs explicit role and ownership checks. The wallet also lacks a clearly visible immutable transaction history and idempotency mechanism for retried operations.

### OTP concurrency and reuse

OTP validation does not visibly consume the OTP after success. Failed-attempt updates use a read-modify-save pattern that may race under concurrent requests.

OTP validation should be single-use, and failed-attempt counters should be updated atomically.

## Marketplace Functional Risks

### Seller matching is not yet a real match

`SellerRepository.findMatchingSellers()` returns active sellers rather than filtering by the search request. Search notifications can therefore be sent to every active seller.

The matching design should define searchable attributes, query indexes, ranking, location behavior, and maximum notification fan-out.

### Identity creation bug risk

The Clerk login flow creates a DTO with a random ID and does not visibly persist the Clerk user ID into the new buyer DTO. When a buyer already exists, the flow may return a newly generated DTO instead of loading the stored buyer.

Identity creation should be idempotent and keyed by the verified external subject.

## Notification Risks

### Retry queue is not atomic

`PushNotificationQueue.removeFromQueue()` reads all sorted-set members into application memory, searches them in Java, and removes a matching member in a separate operation.

Risks:

- Memory growth with large queues
- Concurrent workers can race
- Entries may be removed or processed inconsistently

Use a bounded design and an atomic Redis operation or Lua script.

### Retry scheduling is process-local

Redis stores retry data, but scheduled executor tasks live only in the current JVM. A restart can remove scheduled tasks while Redis entries remain.

Startup recovery must reconstruct due work, and multiple instances must not process the same item without a claim/lease mechanism.

### SSE identity and lifecycle

User-specific SSE subscriptions use a user ID from the URL. Without ownership checks, a caller could subscribe to another user's notifications.

The in-memory connection map also creates multi-instance limitations. Connection replacement, cleanup races, connection limits, and backpressure behavior need integration tests.

### Device-token state split

Device registration is stored in Redis, while `DeviceTokenService` also contains an in-memory token map. Invalid-token cleanup must use one authoritative store; otherwise invalid tokens can remain in Redis and repeatedly fail delivery.

## File and Storage Risks

### Extension-only upload validation

File uploads currently rely heavily on filename extensions. Extensions do not prove that content is a valid image.

Add content-signature validation, MIME validation, size limits, quotas, and safe generated paths. Define cleanup behavior when disk storage succeeds but metadata persistence fails.

### Local filesystem storage

The configured upload path is a relative local filesystem path. This is fragile across container restarts and does not work reliably across multiple application instances.

Use persistent storage for development and object storage for production.

### Path validation

Folder-name validation allows dot characters. The path should be normalized and verified to remain under the configured upload root. Resource access must also enforce ownership.

## Reactive and Resource Risks

- Manually subscribed reactive chains can detach errors and lifecycle management from the request.
- Some asynchronous operations need explicit ownership, retry, shutdown, and recovery behavior.
- Redis range operations collect potentially large result sets.
- Notification and SSE buffers must remain bounded.
- Blocking filesystem work should not run on event-loop threads.

## Configuration and Deployment Risks

### Health-check security mismatch

Spring Security permits `/actuator/health`, but the custom JWT filter's public-endpoint list does not visibly include it. The custom filter may reject health checks before the authorization rule is applied.

### Development defaults

Docker Compose contains development defaults for JWT, Redis, and MongoDB credentials and exposes database ports to the host. This is acceptable only for isolated local development, not production.

### CI coverage and scanning

Tests and JaCoCo are configured, but coverage thresholds are relatively low and CI does not visibly include dependency scanning, secret scanning, or container vulnerability scanning.

### Automatic MongoDB indexes

Automatic index creation is enabled in the main configuration. Production index creation should be deliberate and observable, preferably through a migration or deployment step.

## Documentation and Collaboration Risks

The `production-readiness` backlog is useful but has no explicit status tracking. Several stories describe older code than the current implementation. Each story should be marked as open, in progress, completed, or stale after verification.

The repository now has `AGENTS.md` for human and AI contributors. Agents should read it before changing code, preserve unrelated work, avoid secrets, and report test limitations.

## Recommended Order of Remediation

1. Revoke exposed credentials and remove secret values from documentation.
2. Enforce identity ownership on wallets, notifications, SSE, files, and token registration.
3. Choose and document one active authentication model.
4. Fix identity creation and OTP single-use/concurrency behavior.
5. Define and implement real seller matching.
6. Make notification queues atomic, bounded, restart-safe, and idempotent.
7. Harden file validation and move production storage off the local filesystem.
8. Align wallet implementation with integer minor units and add an auditable ledger.
9. Fix health checks, CI scanning, deployment boundaries, and production index management.
10. Review and update the production-readiness backlog after each verified change.
