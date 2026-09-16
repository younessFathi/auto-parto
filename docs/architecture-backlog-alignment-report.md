# Architecture Backlog Alignment Review

## Scope

This review compares all Markdown files under `.architecture-backlog/` with:

- `docs/architecture.md`
- `docs/deployment-architecture.md`

The backlog contains eight feature indexes and thirty tickets. The review checks whether the backlog covers the target architecture, not whether the tickets have already been implemented.

## Overall Result

**Status: SUBSTANTIALLY ALIGNED, WITH REQUIRED REFINEMENT**

The backlog covers the major architecture decisions:

- PostgreSQL as the system of record
- Reactive persistence and migrations
- Buyer requests, seller listings, and offers
- Authentication and ownership authorization
- Minor-unit wallets and ledger history
- PostgreSQL outbox notifications
- Redis projections and SSE/FCM delivery
- SeaweedFS storage
- OpenBao secrets
- Caddy/TLS deployment
- OpenTelemetry and Prometheus observability
- Recovery and conditional CloudEvents

The backlog does not require an architectural reversal. It needs clearer acceptance criteria, dependencies, ownership boundaries, operational verification, and one missing address-management capability.

## Assessment Response

The separate `architecture-backlog-alignment-assessment.md` was reviewed against the actual tickets. Its corrections are accepted where they reflect existing acceptance criteria or prevent duplicate ownership. The following principles apply:

- Do not import transient OTP, device-token, notification, or outbox state by default during development cutover. Recreate it unless a specific, validated import need exists.
- Keep cross-cutting contracts owned by one ticket and use links/dependencies rather than copying the same requirements into every ticket.
- Do not split tickets unless the parts have independent delivery, ownership, dependency, or failure/test boundaries.
- Treat AP-0001 as `Ready` only after its remaining schema fields are explicitly accepted; otherwise change it to `Proposed`.
- Add an address-management ticket because the target architecture includes addresses but the backlog has no independently deliverable address capability.

## Alignment Legend

- **Aligned**: consistent with the target architecture.
- **Partial**: correct direction but missing material requirements.
- **Update**: wording, dependency, or acceptance criteria must change.
- **Split**: ticket combines independently deliverable concerns.

## Ticket Matrix

| Ticket | Status | Review result |
|---|---|---|
| AP-0001 PostgreSQL schema | Update | The ticket already covers most target tables, UUID/internal-ID rules, indexes, and audit references. Add explicit outbox lease fields, notification attempts, durable exhaustion state, idempotency fingerprints, and device-token details. Keep the ticket `Ready` only after those refinements are approved; otherwise use `Proposed`. Do not create a duplicate schema ticket. |
| AP-0002 Reactive persistence | Partial | Add optimistic locking/guarded updates and explicitly separate R2DBC runtime access from JDBC migration tooling. |
| AP-0003 Development cutover | Aligned / Update | JDBC migration execution, expand-contract migration, rollback/forward-fix, and clean PostgreSQL replacement are already covered. Clarify that transient OTP, device-token, notification, and outbox state is recreated by default, not imported. A one-time import is exceptional and must be explicitly validated. |
| AP-0004 Canonical authentication | Partial | Add issuer, audience, claims, provider-subject mapping, clock skew, expiry, signing-key rotation, and canonical principal requirements. Link to AP-0022. |
| AP-0005 Identity impersonation | Aligned | Strong coverage. Explicitly include wallets, files, notifications, device tokens, and protected diagnostics in ownership tests. |
| AP-0006 Secure OTP | Partial | Atomic consumption is already covered. Add challenge purpose/identity binding, authentication rate limits, Redis failure behavior, and security audit references. |
| AP-0007 Part requests | Partial | Searchable fields and guarded lifecycle transitions are already covered. Add only public-ID/API behavior and explicit notification/outbox dependencies where request mutations emit events. |
| AP-0008 Part listings | Partial | Searchable fields and guarded lifecycle transitions are already covered. Add only public-ID/API behavior and explicit notification/outbox dependencies where listing mutations emit events. |
| AP-0009 Offers and matching | Partial | Define offer lifecycle, request/listing relationship rules, idempotency, transaction boundaries, and whether matching is synchronous or outbox-driven. Consider splitting offers from matching/fan-out. |
| AP-0010 Money minor units | Partial | Add an exhaustive migration inventory for current `BigDecimal` models, DTOs, mappers, repositories, APIs, fixtures, and tests. Define operation-specific negative-value rules. |
| AP-0011 Wallet ledger | Aligned | Strong coverage. Add currency/sign constraints, transaction references, restore reconciliation, and wallet ownership authorization tests. |
| AP-0012 Notification outbox | Partial | Add durable event states, lease fields, event type/schema version, correlation context, ordering key, retention, and separate transport-attempt records. |
| AP-0013 Outbox processing | Aligned | Atomic claims, leases, bounded retries, dead-letter handling, replay authorization, and ordering are already covered. Clarify durable PostgreSQL exhaustion state versus reconstructible Redis projections and add exact retention/metrics if absent. |
| AP-0014 Device tokens | Aligned | Add provider-token redaction, platform validation, conflict semantics, cleanup, and reconciliation requirements. |
| AP-0015 SSE/FCM delivery | Aligned | Closely matches the architecture. Name Redis Streams or the approved equivalent as the initial fan-out mechanism and define replay-retention sizing. Consider splitting SSE and FCM if independently delivered. |
| AP-0016 Redis projections | Partial | Add a key-family inventory, Redis Streams semantics, lease/dead-letter rules, and rebuild/rescheduling from PostgreSQL outbox. |
| AP-0017 Storage boundary | Aligned | Require the S3 gateway abstraction explicitly, not direct SeaweedFS SDK use, and define checksum/object-existence operations. |
| AP-0018 Staged file lifecycle | Aligned | Add public file UUIDs, staged-object expiry, download authorization, and metadata/object failure behavior. |
| AP-0019 Storage reconciliation | Partial | Add a state/action matrix, dry-run mode, retention windows, destructive-action authorization, metrics, and ownership split with AP-0027. |
| AP-0020 Stateless deployment | Partial | Add liveness/readiness, pool sizing, SSE multi-instance behavior, worker lease recovery, and bounded shutdown requirements. |
| AP-0021 TLS entrypoint | Aligned | Trusted proxies, TLS, health checks, SSE timeouts, and private database boundaries are already covered. Add only concrete initial Caddy configuration and buffering details if implementation needs them. |
| AP-0022 OpenBao secrets | Aligned | JWT rotation and non-root workload identity are already covered. Add dynamic PostgreSQL credential policy, Transit/KMS decision, and unseal/recovery-key handling where not already explicit. |
| AP-0023 Telemetry/health | Aligned | Add exact backend decision, oldest pending age, retry/DLQ age, PostgreSQL locks/replication, Redis evictions, SeaweedFS drift, and recovery-drill metrics. |
| AP-0024 Alerts/recovery | Partial | RPO/RTO, PITR, wallet reconciliation, Redis rebuild, SeaweedFS recovery, rollback, and runbooks are already covered. Add provisional numeric RPO <= 15 minutes and RTO <= 2 hours, name OpenBao recovery explicitly, and require restore-order drill evidence. |
| AP-0025 API error contract | Aligned / Partial | Safe errors, stable behavior, and status codes are covered. Add response schema, validation field errors, and the resource-existence disclosure policy if not already explicit. |
| AP-0026 Data infrastructure | Partial | Add WAL/PITR, lock/slow-query/replication monitoring, Redis TLS/authentication, and links to AP-0024. |
| AP-0027 SeaweedFS hardening | Aligned | Clarify replication is not backup, object version/deletion policy, and private/admin-only routing. |
| AP-0028 Provider isolation | Aligned | Add all synchronous SDKs to the provider inventory and define which operations return `202` versus retry synchronously. |
| AP-0029 CloudEvents interoperability | Aligned | Keep blocked until an external consumer, webhook, broker, or second service exists. Do not use CloudEvents for logs. Add a decision record when unblocked. |
| AP-0030 Application boundaries | Aligned | Add explicit internal-ID exposure and direct provider-SDK prohibitions only if not already covered. Clarify overlap with AP-0002 rather than duplicating persistence criteria. |

## Feature Index Review

### Missing Address Capability

The target architecture includes an `addresses` aggregate, but the backlog has no independently deliverable address-management ticket. Add a ticket covering:

- Create, update, retrieve, and delete operations.
- Ownership derived from the authenticated principal.
- Role and resource-ownership authorization.
- Validation and safe API errors.
- Public UUID and internal-ID boundaries, owned by AP-0001 and referenced by the address ticket.
- PostgreSQL constraints and indexes.
- Success, validation, missing-address, and cross-user denial tests.

### F01 Platform and Data

Aligned directionally. Add identifier strategy, MongoDB import boundaries, R2DBC/JDBC separation, and clarify AP-0030 versus AP-0002 ownership.

### F02 Identity and Security

Aligned directionally. Add OpenBao/JWT key rotation, issuer/audience validation, rate-limit failure policy, and protected operator diagnostics where those requirements are not already present in AP-0004, AP-0006, AP-0022, or AP-0023.

### F03 Marketplace

Aligned with the hybrid request/listing model. Add outbox side effects, public IDs, idempotency, indexed query plans, and explicit offer/matching dependencies.

### F04 Money and Wallets

Strongly aligned. Add restore-time ledger reconciliation and the complete migration away from current `BigDecimal` APIs and persistence types. Do not duplicate the full identifier or schema contract here; link AP-0001.

### F05 Notifications and Redis

Strongest grouping. Explicitly separate PostgreSQL durable event state, Redis projections, and SSE/FCM transport-attempt state.

### F06 File Storage

Strongly aligned. Make the dependency between SeaweedFS provider recovery and application-level reconciliation explicit.

### F07 Deployment and Secrets

Strongly aligned. Clarify ownership with F08 for PostgreSQL backups, Redis recovery, OpenBao recovery, and operational drills.

### F08 Observability and Recovery

Aligned but incomplete. Explicitly own RPO/RTO approval, WAL/PITR, OpenBao recovery, and restoration acceptance tests. AP-0024 already covers most of these and should receive narrow refinements rather than a duplicate recovery ticket.

## Cross-Cutting Gaps

### PostgreSQL

The backlog should explicitly cover:

- R2DBC runtime versus separate JDBC migration job.
- WAL archiving and point-in-time recovery.
- Connection-pool sizing and lock, slow-query, and replication monitoring.
- Expand-contract migrations for overlapping application versions.
- Migration rollback versus forward-fix policy.
- Durable idempotency, outbox claims, notification attempts, retry/dead-letter state, audit records, and device tokens.

### Identifier Strategy

AP-0001 should own the full identifier contract. Dependent tickets should link to it and repeat only capability-specific behavior:

- Internal PostgreSQL `BIGINT`: joins and internal repository/service boundaries only.
- External UUID: APIs, URLs, events, and public resource references.
- Trace/correlation ID: observability context only.
- Idempotency key: caller-supplied, owner/operation scoped, fingerprinted, retained, and conflict-checked.
- Authorization: derived from the authenticated principal, never from an ID alone.

### Authentication and Authorization

AP-0004/AP-0005 need explicit coverage for issuer, audience, claims, provider subject, key rotation, clock skew, rate-limit failure, protected diagnostics, and ownership tests for wallets, files, notifications, and device tokens.

### Outbox, Redis, and SSE

AP-0012 owns durable event creation and state. AP-0013 owns claims, leases, retries, ordering, exhaustion, and replay. AP-0015 owns SSE/FCM transport attempts. AP-0016 owns Redis classification and reconstruction. Add links and missing acceptance details without copying every requirement into every ticket.

### Storage

AP-0017/AP-0018/AP-0019/AP-0027 should clearly divide responsibilities:

- Application: metadata, ownership, lifecycle, and reconciliation.
- SeaweedFS: object bytes and provider configuration.
- Recovery operations: provider restore, key recovery, and post-restore reconciliation.

### CloudEvents

AP-0029 is correctly blocked. The internal versioned DTO plus PostgreSQL outbox is sufficient until another service, broker, webhook, or external consumer requires a stable interoperability contract.

## Dependency-Safe Recommended Sequence

1. AP-0001: PostgreSQL schema and identifier contract.
2. AP-0002: reactive persistence.
3. AP-0004/AP-0005/AP-0006: authentication, ownership, and OTP.
4. AP-0025, then AP-0030: API errors followed by application-boundary enforcement.
5. AP-0007/AP-0008/AP-0009 and the address ticket: marketplace aggregates and matching.
6. AP-0010/AP-0011: minor-unit money and wallet ledger.
7. AP-0012/AP-0013/AP-0014/AP-0015/AP-0016: outbox, processing, device tokens, delivery, and Redis projections.
8. AP-0017/AP-0018/AP-0019: storage boundary, lifecycle, and reconciliation.
9. AP-0003: development cutover after persistence and storage dependencies.
10. AP-0020: stateless application deployment.
11. AP-0021/AP-0022/AP-0023: TLS, OpenBao, and telemetry.
12. AP-0026/AP-0027/AP-0028: data infrastructure, SeaweedFS hardening, and provider isolation.
13. AP-0024: alerts and recovery after infrastructure, secrets, telemetry, and storage dependencies.
14. AP-0029: only after an external interoperability requirement exists.

## Tickets Worth Splitting

Only split a ticket when independent ownership, release, dependency, failure, or test boundaries are confirmed. AP-0009 and AP-0015 may justify later splits; the other previously suggested splits should remain single tickets unless implementation planning proves otherwise.

## Conclusion

The backlog substantially covers the target architecture. The assessment correctly narrowed the remediation plan. The main work is refinement, not replacement:

- Make PostgreSQL durability and migration operations explicit.
- Apply the public/internal identifier contract across all tickets.
- Close authentication configuration and key-rotation gaps.
- Separate durable outbox state, Redis projections, and transport attempts.
- Name the initial SSE fan-out mechanism.
- Define recovery ownership and measurable RPO/RTO.
- Add the missing address-management capability.
- Clarify dependencies without duplicating ticket ownership.
- Split tickets only where independent delivery or failure boundaries justify it.

## Verification

- Reviewed all Markdown files under `.architecture-backlog/`.
- Reviewed `docs/architecture.md`.
- Reviewed `docs/deployment-architecture.md`.
- No application tests were run because this was a backlog/design review.
