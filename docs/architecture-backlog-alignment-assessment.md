# Architecture Backlog Alignment Assessment

## Purpose

This assessment evaluates whether the team should apply the recommendations in `docs/architecture-backlog-alignment-report.md` to `.architecture-backlog/`.

It uses the approved target documents as the source of truth:

- `docs/architecture.md`
- `docs/deployment-architecture.md`

## Decision

**Do not align the backlog with the alignment report as currently written.**

The report's overall conclusion is reasonable: the backlog substantially covers the architecture and needs refinement rather than replacement. However, several detailed recommendations are inaccurate, duplicate requirements already owned by other tickets, violate explicit dependencies, or introduce unsafe development-data migration scope.

The alignment report should be corrected first. The backlog should then adopt only the validated refinements.

## Recommendations to Accept

### Add an address-management ticket

The target architecture includes owner-bound addresses, but the backlog does not provide an independently deliverable address capability.

The new ticket should cover:

- Address creation, update, retrieval, and deletion.
- Ownership derived from the authenticated principal.
- Role and resource-ownership authorization.
- Validation and safe API errors.
- Public UUID and internal identifier boundaries.
- Relevant database constraints and indexes.
- Success, validation, missing-address, and cross-user denial scenarios.

### Refine AP-0001 schema details

`AP-0001` should explicitly confirm schema support for:

- Outbox lease ownership and expiry.
- Notification transport attempts.
- Durable exhaustion or dead-letter state.
- Device-token records.
- Idempotency records and request fingerprints.
- Audit references for financial and security-sensitive changes.

These are refinements to the schema ticket, not reasons to create duplicate schema tickets.

### Refine authentication requirements

`AP-0004` should explicitly cover:

- Issuer and audience validation.
- Required claims and provider-subject mapping.
- Expiry and permitted clock skew.
- Signing-key selection and rotation behavior.
- Canonical principal fields.
- Dependency on `AP-0022` where external signing keys or provider secrets are required.

### Refine OTP security requirements

`AP-0006` should add:

- Challenge purpose and identity binding.
- Authentication rate limits and Redis-failure behavior.
- Security audit references without recording OTP values.

Atomic consumption is already covered and should not be reported as missing.

### Refine operational details where genuinely absent

Valid focused refinements include:

- Add provisional RPO of 15 minutes or better and RTO of 2 hours or better to `AP-0024`.
- Explicitly include OpenBao recovery in `AP-0024`.
- Add PostgreSQL lock, slow-query, and replication monitoring where not already explicit.
- Define the initial Redis Streams consumer/fan-out semantics and retention sizing.
- Clarify that PostgreSQL owns durable exhausted-event state while Redis dead-letter data is reconstructible.

## Recommendations to Reject

### Reject importing transient security and delivery state

`architecture-backlog-alignment-report.md:46` recommends adding OTP challenges, device tokens, notifications, and outbox state to the development import inventory.

This should not be adopted as a default requirement.

The architecture permits only deliberately selected local-data imports. Importing transient state can cause:

- Reuse of expired or consumed OTP challenges.
- Duplicate notification delivery.
- Invalid device-token ownership.
- Reprocessing of stale outbox events.
- Confusion about the authoritative source during cutover.

Default development cutover behavior should recreate transient state. Any exceptional import must have a specific need, validation rules, expiry handling, idempotency, and reconciliation.

### Reject repeating identifier rules in every ticket

`architecture-backlog-alignment-report.md:122-130` recommends repeating the complete identifier contract across migration, marketplace, file, event, error, and boundary tickets.

This creates duplication and future inconsistency.

The identifier contract should have one authoritative owner, currently `AP-0001`, with dependent tickets linking to it. A dependent ticket should repeat only identifier behavior unique to that capability.

### Reject duplicate outbox and Redis responsibilities

The report asks multiple tickets to repeat requirements already separated correctly:

- `AP-0012` owns durable event creation and state.
- `AP-0013` owns claims, leases, retries, ordering, exhaustion, and replay.
- `AP-0015` owns SSE and FCM transport-attempt behavior.
- `AP-0016` owns Redis key classification, bounds, and PostgreSQL reconstruction.

These boundaries should remain. Cross-ticket dependencies should be clarified instead of copying all requirements into every ticket.

### Reject duplicate storage responsibilities

Storage ownership is already divided among:

- `AP-0017`: provider-neutral storage boundary.
- `AP-0018`: authorization, validation, and staged file lifecycle.
- `AP-0019`: application metadata/object reconciliation.
- `AP-0027`: SeaweedFS security, durability, backup, and restoration.
- `AP-0024`: cross-system recovery drills.

Do not add checksum validation to the storage-boundary ticket or public-ID design to the upload-lifecycle ticket when those concerns already have authoritative owners.

### Reject unnecessary ticket splitting

The report proposes several splits without demonstrating that each new part has independent value, implementation, or acceptance.

Do not split a ticket only because it covers several related acceptance criteria. Split only when:

- Parts can be implemented and released independently.
- Parts have different owners or dependencies.
- The ticket cannot be completed safely in one reviewable change.
- Separate failure and test boundaries exist.

`AP-0009` and `AP-0015` may justify later splits if implementation planning confirms independent delivery. The other proposed splits currently risk creating overlapping ownership.

## Incorrect Missing-Requirement Claims

The alignment report describes several existing requirements as absent. These claims should be corrected rather than copied into the backlog.

Already covered:

- Atomic OTP consumption in `AP-0006`.
- Searchable fields and guarded state transitions in `AP-0007` and `AP-0008`.
- Atomic claims, leases, bounded retries, dead-letter handling, replay authorization, and ordering in `AP-0013`.
- Stable notification IDs and versioned durable events in `AP-0012`.
- Channel-specific transport states in `AP-0015`.
- Redis key-family bounds and PostgreSQL reconstruction in `AP-0016`.
- Trusted proxies, TLS, and SSE timeout behavior in `AP-0021`.
- JWT rotation and non-root workload identity in `AP-0022`.
- Safe error responses and stable error behavior in `AP-0025`.
- JDBC migration execution, expand-contract policy, and rollback or forward-fix handling in `AP-0003`.

## Status Correction

`AP-0001` currently has status `Ready`, while the alignment report proposes material refinements.

Use this rule:

- Keep `Ready` only if the schema refinements are added and the ticket requires no further architectural decision.
- Change to `Proposed` if schema fields, ownership, or persistence boundaries still need agreement.

The report should explicitly call out this status decision rather than describing the ticket as materially incomplete while leaving it ready for implementation.

## Corrected Dependency Sequence

The sequence in `architecture-backlog-alignment-report.md:152-166` is not executable because it schedules tickets before their declared dependencies.

A dependency-safe sequence for the currently declared links is:

1. `AP-0001`: PostgreSQL schema and identifier contract.
2. `AP-0002`: reactive persistence.
3. `AP-0004`, `AP-0005`, `AP-0006`: authentication, ownership, and OTP.
4. `AP-0025`, then `AP-0030`: API errors followed by application-boundary enforcement.
5. `AP-0007`, `AP-0008`, `AP-0009`: requests, listings, offers, and matching.
6. `AP-0010`, `AP-0011`: money and wallet ledger.
7. `AP-0012`, `AP-0013`, `AP-0014`, `AP-0015`, `AP-0016`: outbox, processing, tokens, delivery, and Redis projections.
8. `AP-0017`, `AP-0018`, `AP-0019`: storage boundary, lifecycle, and reconciliation.
9. `AP-0003`: development cutover after its persistence and storage dependencies.
10. `AP-0020`: stateless application deployment after reactive persistence and Redis projections.
11. `AP-0021`, `AP-0022`, `AP-0023`: TLS entry point, OpenBao, and telemetry after application deployment and their other dependencies.
12. `AP-0026`, `AP-0027`, `AP-0028`: data infrastructure, SeaweedFS hardening, and provider isolation.
13. `AP-0024`: alerts and recovery after infrastructure, secrets, telemetry, and storage dependencies.
14. `AP-0029`: CloudEvents only after a real external interoperability requirement exists.

The address ticket should be inserted after authentication and ownership foundations and before final cutover.

## Corrected AP-0024 Assessment

`AP-0024` already includes:

- Approved RPO/RTO.
- Point-in-time recovery.
- Wallet-ledger reconciliation.
- Redis projection rebuilding.
- SeaweedFS recovery.
- Secret rotation.
- Application rollback.
- Incident ownership and runbooks.

Its status should not be broadly `Partial` for missing these requirements. Remaining refinements are narrow:

- Add the provisional numeric RPO/RTO targets.
- Name OpenBao recovery explicitly.
- Ensure restore order and incident communication are included in drills.

## Required Changes to the Alignment Report

Before using the alignment report to modify the backlog:

1. Correct the invalid dependency sequence.
2. Remove the default requirement to import transient OTP, token, notification, and outbox state.
3. Remove claims that existing requirements are missing.
4. Replace repeated cross-ticket requirements with authoritative ownership and dependency links.
5. Remove or justify proposed ticket splits.
6. Correct the `AP-0024` classification.
7. Add the missing address-management capability.
8. Reconcile `AP-0001` refinements with its `Ready` status.

## Final Recommendation

**Revise the alignment report to match the approved architecture and actual backlog. Do not rewrite the backlog to match the current report.**

After correction, use the report as a refinement checklist. The backlog's structure and architectural decomposition are fundamentally sound.

## Verification

- Reviewed `docs/architecture-backlog-alignment-report.md`.
- Reviewed all feature and ticket files under `.architecture-backlog/`.
- Compared claims with `docs/architecture.md` and `docs/deployment-architecture.md`.
- No application tests were run because this is a documentation and backlog assessment.
