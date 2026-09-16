# Auto-Parto Target Architecture

## Purpose

Auto-Parto is a reactive backend for a car-parts marketplace. Buyers publish part requests, sellers publish part listings or respond with offers, and the platform manages authentication, wallets, files, and notifications.

The target architecture keeps the existing Spring WebFlux foundation but replaces MongoDB as the primary database with PostgreSQL. This makes identity, money, persistence, and asynchronous delivery explicit and safe.

## High-Level Design

```text
Client applications
        |
        v
WebFlux API and SSE controllers
        |
        v
Application services
        |
        +--> Domain validation and authorization
        +--> Application PostgreSQL repositories
        +--> Audit outbox publisher
        +--> Redis adapters
        +--> Notification gateway
        +--> File storage gateway
        |
        v
Application PostgreSQL, audit PostgreSQL, Redis,
Firebase, email provider, object storage
```

## Package Responsibilities

- `api`: HTTP and SSE transport only. Controllers validate input, obtain the authenticated identity, call an application service, and map the result to an API response.
- `service`: Business use cases such as creating a part request or listing, making an offer, changing a wallet balance, validating an OTP, and sending a notification.
- `repository`: Persistence operations. SQL and database-specific details must stay here.
- `model`: Relational persistence entities and persistence-oriented fields.
- `dto`: API request and response contracts. DTOs must not expose persistence implementation details.
- `mapper`: MapStruct mappings between persistence entities and DTOs.
- `config`: Security, Redis, Firebase, executors, profiles, and application infrastructure.
- `service/notification`: Notification orchestration, delivery providers, retry handling, and pending-message storage.

## Request Flow

1. The request enters through a WebFlux controller.
2. Authentication middleware validates the token and creates one canonical principal.
3. The controller passes the authenticated identity and validated request to an application service.
4. The service checks role and resource ownership before changing data or sending notifications.
5. Repositories perform bounded, reactive persistence operations against PostgreSQL.
6. The service returns a result or a domain exception.
7. The shared error handler maps safe domain errors to consistent HTTP responses.

Client-supplied buyer, seller, or user IDs are references only. Sensitive operations must derive the acting identity from the authenticated principal.

## Authentication and Authorization

Use one documented authentication model for each deployment profile. The preferred flow is:

```text
External identity provider or OTP
        -> token validation
        -> canonical authenticated principal
        -> role check
        -> resource ownership check
```

Authorization has two separate decisions:

- Role authorization: Is this identity allowed to perform this type of operation?
- Ownership authorization: Is this identity allowed to access this specific buyer, seller, wallet, file, or notification?

Unfinished authentication integrations must be disabled unless their issuer, keys, claims, and tests are configured for that profile.

## Money and Wallets

Money is stored as integer minor units:

```text
1000 cents = 10.00 USD
```

Rules:

- Use `long`, not `Integer`, for balances and prices.
- Store the currency with every monetary value.
- Apply the currency's correct minor-unit scale.
- Define and validate the supported ISO currencies, including zero- and three-decimal currencies where supported.
- Never use `double` or `float`.
- Never convert money through floating-point values.
- Use overflow-safe arithmetic such as `Math.addExact`.
- Validate amount, currency, and sign before persistence.
- Reject excess precision at API boundaries unless the specific operation documents an explicit rounding policy.
- Reject arithmetic across currencies unless an explicit exchange operation exists.
- Update balances atomically in PostgreSQL.
- Record an immutable transaction or ledger entry for every balance change.
- Support an idempotency key for retried deposits, withdrawals, and payments.

The current code uses `BigDecimal` in parts of the wallet implementation. A migration to minor-unit `long` values must update models, DTOs, repositories, mappings, tests, and API documentation together.

## PostgreSQL Design

- Use Spring Data R2DBC or another approved non-blocking PostgreSQL client for WebFlux request paths.
- Use database transactions for operations that change multiple related records.
- Use row-level locks or guarded conditional updates for balances and state transitions.
- Use foreign keys, unique constraints, check constraints, and not-null constraints to protect data integrity.
- Add indexes for every production query pattern and verify them with query plans.
- Manage schema changes with versioned migrations such as Flyway or Liquibase.
- Keep migrations explicit and reviewable; do not rely on automatic production schema creation.
- Use optimistic locking or guarded updates where concurrent edits are possible.
- Use PostgreSQL `JSONB` only for genuinely flexible attributes, not as a replacement for relational fields.
- Keep the application PostgreSQL resource separate from the dedicated audit PostgreSQL resource. They use independent credentials, connection pools, backups, monitoring, resource limits, and failure boundaries.

### Suggested relational boundaries

- `users`: canonical identity, provider subject, email, role, and status.
- `buyers` and `sellers`: role-specific profile data linked to `users`.
- `part_requests`: buyer-owned requests for needed parts with searchable vehicle and part fields.
- `part_listings`: seller-owned inventory or sale listings with searchable vehicle and part fields.
- `offers`: buyer/seller responses or negotiations linked to a request or listing.
- `wallets`: one current balance per owner and currency.
- `wallet_ledger`: immutable deposits, deductions, refunds, and payment references.
- `addresses`: normalized owner addresses with explicit ownership.
- `otp_challenges`: hashed, expiring, single-use verification challenges.
- `files`: metadata and object-storage references, not large binary content.

## Composition and Inheritance Boundaries

Use composition for most Auto-Parto domain relationships. A relationship such as `Seller has a Wallet` should be represented by a separate wallet entity linked through a foreign key, not by making `Wallet` a superclass or subclass of `Seller`.

Examples of composition:

- `User` has a buyer profile or seller profile.
- `Seller` has a wallet and has many part listings.
- `Buyer` has many offers.
- `Wallet` has many immutable ledger entries.
- A notification service has an FCM provider or SSE provider.
- An application service has repository dependencies.

Composition gives each entity or service a clear ownership boundary, supports independent testing, and maps naturally to PostgreSQL tables and foreign keys.

Use inheritance only for genuine substitutable behavior. For example, different notification implementations may implement a shared notification-provider contract when callers can use every implementation safely through that contract.

Avoid using inheritance merely to reuse fields or methods. Deep entity hierarchies make PostgreSQL mapping, API serialization, authorization, and lifecycle rules harder to understand.

For every proposed inheritance relationship, verify:

- The child is truly substitutable for the parent.
- The parent contract is stable and meaningful.
- Authorization and persistence rules remain clear.
- The hierarchy does not expose internal persistence details through the API.

Important domain data should include:

- Created and updated timestamps
- Explicit status values
- Version or concurrency information where needed
- Immutable wallet ledger entries for financial changes.
- Stable audit-event UUID references for security-sensitive changes; high-volume audit history is stored in the dedicated audit resource.

### Aggregate ownership and lifecycle

The marketplace uses a hybrid model and does not overload one `Piece` entity:

- A buyer owns a `part_request` and may cancel or close it.
- A seller owns a `part_listing` and may publish, pause, reserve, or close it.
- An `offer` belongs to its buyer and seller participants and references the request or listing it answers.
- Matching connects requests to listings or eligible sellers; it does not transfer ownership.

Each aggregate must have explicit guarded state transitions. Business status, delivery status, and storage status must not be collapsed into one shared status enum.

## Redis Design

Redis is an infrastructure adapter, not the source of truth for relational or financial data.

- Use bounded keys, lists, and sorted sets.
- Set TTLs intentionally and verify them in tests.
- Use atomic commands, transactions, or Lua scripts for multi-step operations.
- Do not load unbounded collections into application memory.
- Design queued work so another application instance can recover it after restart.
- Use idempotency or deduplication for retryable work.
- Make rate-limit behavior during Redis failure an explicit security decision.

Recoverable notification work must be reconstructible from PostgreSQL outbox rows. Redis is an accelerator or delivery projection unless a separate non-evicting queue deployment is explicitly approved.

## Audit Storage Architecture

The application PostgreSQL database is authoritative for business state, wallet balances, immutable wallet ledger entries, and the durable outbox. High-volume historical audit events are stored in a separate audit PostgreSQL resource with independent compute, storage, credentials, backups, retention, and access controls.

Business changes and their audit-outbox events are committed in one application-database transaction. A bounded worker publishes the events asynchronously to the audit database using stable event UUIDs and at-least-once delivery. Audit ingestion is idempotent, retryable, and independently observable. The application remains available for business operations while the audit database is unavailable, subject to bounded outbox growth and explicit delivery-lag alerts.

Audit data must not become the source of truth for balances, ownership, permissions, or marketplace workflow state. Redis may accelerate audit publishing, but the application outbox remains the durable recovery source.

## Notification Architecture

Notification delivery should be modeled as an asynchronous workflow:

```text
Create notification intent
        -> persist business change and outbox event in one PostgreSQL transaction
        -> claim persisted event
        -> enqueue/project when needed
        -> deliver through SSE or FCM
        -> record transport result
        -> retry or mark exhausted
        -> remove invalid device tokens
```

Requirements:

- Do not lose messages during application restart.
- Bound per-user pending messages and retry attempts.
- Classify failures as retryable or permanent.
- Do not retry invalid device tokens.
- Make delivery idempotent where duplicate delivery is possible.
- Protect user-specific notification endpoints with ownership checks.
- For multiple application instances, use shared state or a dedicated broker for connection and delivery coordination.

SSE delivery semantics are explicit and channel-specific. Durable event state is separate from transport-attempt state:

- Durable event: `PENDING`, `DISPATCHED`, `EXHAUSTED`.
- SSE attempt: `WRITTEN`, `FAILED`, or `EXPIRED`.
- FCM attempt: `ACCEPTED`, `REJECTED`, `INVALID_TOKEN`, or `FAILED`.

`RECEIVED` and `PROCESSED` are never implied by a server write or provider acceptance; they require client acknowledgement. Events have stable IDs, bounded retention, `Last-Event-ID` replay, and deduplication. Client acknowledgements are only required for business workflows that need processed confirmation.

## File Storage

The application should use a file-storage abstraction with separate implementations for local development and object storage in production.

Every upload must validate:

- Authenticated ownership
- File size
- MIME type
- File signature/content
- Image dimensions where relevant
- Safe generated destination path
- Per-user or per-request/listing quota

Authenticate and authorize request/listing ownership before creating upload metadata, issuing an upload destination, or accepting bytes. Database metadata and stored content need a recovery or cleanup strategy when one operation succeeds and the other fails. Use `PENDING`, `ACTIVE`, and `TOMBSTONED` storage states with staged object keys, idempotent finalization, asynchronous deletion, and bounded orphan reconciliation.

## Error Handling

Use consistent responses:

- `400`: invalid input
- `401`: missing or invalid authentication
- `403`: authenticated but not authorized
- `404`: resource not found
- `409`: state or idempotency conflict
- `202`: accepted asynchronous work
- `500`: unexpected server failure

Internal exception messages, credentials, tokens, OTPs, and provider payloads must not be returned to clients.

## Observability

The service should provide:

- Correlation/request IDs
- Structured logs without secrets or sensitive payloads
- Health checks that do not require a user JWT
- Metrics for request failures, wallet operations, queue depth, retry count, notification delivery, and active SSE connections
- Alerts for dead-letter growth, authentication abuse, storage failures, and dependency outages

## Deployment Boundaries

Detailed deployment, persistence, storage, and observability guidance is in [`deployment-architecture.md`](deployment-architecture.md).

- Local profile: Docker Compose and safe development defaults.
- Test profile: Testcontainers or isolated test doubles with no real credentials.
- Production profile: environment or secret-manager configuration, restricted PostgreSQL/Redis networking, versioned database migrations, explicit indexes, persistent object storage, resource limits, and deployment health checks.

Production must not depend on local relative filesystem paths, development passwords, committed credentials, or host-exposed database ports.

## Migration From Current MongoDB Implementation

The current codebase uses reactive MongoDB. PostgreSQL is the future architecture, so migration should be planned rather than mixed casually into feature work.

1. Define the relational schema and ownership rules.
2. Add versioned PostgreSQL migrations and repository tests.
3. Implement PostgreSQL repositories behind the existing service contracts.
4. Recreate fixtures and migrate only deliberately selected local data through a one-time idempotent import if needed.
5. Reconcile imported identity, requests, listings, offers, wallets, and ledger history.
6. Migrate file metadata while moving binary content to object storage.
7. Remove MongoDB dependencies after PostgreSQL tests and the development cutover are verified.

Live dual-read, dual-write, or controlled cutover is reserved for a future environment containing durable user data.

Do not run MongoDB and PostgreSQL as two independent sources of truth for the same entity without a documented synchronization and recovery strategy.

Contribution and architecture review checks are maintained in the root [`AGENTS.md`](../AGENTS.md) so human and AI contributors use one canonical checklist.
