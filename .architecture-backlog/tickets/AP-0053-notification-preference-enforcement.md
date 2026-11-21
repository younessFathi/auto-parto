# AP-0053 Enforce Notification Preferences in Delivery

- **Status:** In Progress
- **Type:** Bug Fix
- **Feature:** [F13 Marketplace Business Integrity](../features/F13-marketplace-business-integrity.md)
- **Linked Tickets:** [AP-0052 Accepted-Offer Journey](AP-0052-accepted-offer-marketplace-journey.md), [AP-0042 Delivery Reliability](AP-0042-notification-delivery-reliability.md), [AP-0043 Versioned SSE](AP-0043-versioned-sse-delivery-contract.md)
- **Source:** `NotificationPreferenceFilter`, `NotificationPreferencesService`, existing notification dispatch paths; 2026-11-18 business-integrity assessment

## Description

Make recipient preferences affect actual approved delivery paths and make their effective defaults consistent with the preferences API.

[`NotificationPreferenceFilter.java:12–29`](../../src/main/java/auto/parto/service/notification/NotificationPreferenceFilter.java) provides channel/type checks, but source inspection finds no production delivery callers. [`NotificationPreferencesService.java:19–29`](../../src/main/java/auto/parto/service/NotificationPreferencesService.java) returns true for missing-record boolean helpers while its full DTO defaults SMS and marketing to false; unknown channel/type values also currently return true. These observed mismatches do not decide the desired consent/error policy.

Scope is preference interpretation and enforcement across existing applicable direct, queued and retried delivery paths. No new SMS/email/push provider, new marketing system, transport implementation or rewrite of the delivery reliability engine is authorized.

## Decisions and Dependencies Before Ready

- Owner must approve which existing notification types/channels are preference-controlled, how channel and type combine, missing-record defaults and unknown identifier handling. Explicitly decide any mandatory/security/operational exception and its authorized producer; do not infer an admin bypass.
- Decide the policy for preference lookup failure/unavailability and whether queued/retried sends use current preferences or an approved snapshot. Define how a preference change affects an already queued message and any SSE/live-delivery applicability.
- Define accurate suppression/pending outcomes and bounded recovery; suppressed work must not be reported as delivered or retried indefinitely. Use AP-0042/AP-0043 as boundaries/reference only; their Done scope stays unchanged.

## Acceptance Criteria

1. Record the approved type/channel/default/exception/error/timing matrix before Ready, including currently active delivery paths and explicitly non-applicable paths; no new provider/channel capability is implied.
2. Full preference DTO reads, per-channel/per-type helpers and dispatch decisions derive the same effective policy for stored and absent records. Unknown/invalid type/channel handling is explicit and cannot accidentally bypass an opt-out.
3. All existing applicable delivery entry points enforce the combined recipient channel/type policy at the approved decision time, including direct sends, durable processing, retries and replay/live paths where selected. No unexamined producer, admin route or retry path bypasses the policy.
4. Preference updates and queued/retried processing follow the selected timing semantics under concurrency. A lookup failure uses the approved safe suppression/pending/error policy without a false delivered result, unbounded retries or silent loss of required intent.
5. Preserve durable delivery/lease/retry invariants and distinguish sent, suppressed and pending outcomes accurately in existing business/delivery reporting. Do not expose message contents or private preference data in logs or other recipients' APIs.
6. Cover each approved exception and default in API and user documentation so a displayed opt-out matches actual behavior. Keep authenticated-owner preference management and existing notification authorization intact.

## Test Scenarios

1. **Given** every approved stored channel/type combination, **when** each applicable direct/queued/retry path dispatches, **then** allowed messages follow normal delivery and opted-out messages are suppressed as documented.
2. **Given** no preference record, **when** full DTO, helpers and actual delivery are evaluated, **then** they agree with the approved defaults, including explicit SMS/marketing decisions.
3. **Given** unknown channel/type identifiers or an attempted producer/admin bypass, **when** dispatched, **then** the approved validation/exception policy is enforced without accidental opt-out bypass.
4. **Given** preferences change while work is queued or retried, **when** dispatch races or resumes after restart, **then** the approved current/snapshot policy holds without duplicate logical intent/work caused by preference evaluation or misleading outcomes. Stable event identity and existing at-least-once delivery, bounded retry/replay and client-deduplication semantics remain intact; exactly-once transport is not promised.
5. **Given** lookup/database/provider failure, **when** processing retries or recovers, **then** suppression/pending/delivery states remain accurate, retry work is bounded and required intent is not silently lost.
6. **Given** an explicitly approved mandatory-message exception, **when** an authorized or unauthorized producer uses it, **then** only the approved exception can apply and its behavior is documented; otherwise no implicit exception exists.

## Definition of Done

The approved preference policy is consistent across reads/helpers and all applicable existing delivery paths; every criterion/scenario is automated and passing; suppression/recovery reporting and user/API documentation are accurate; no new provider or transport scope is introduced; and all applicable test/verification suites pass. No criterion or scenario may be deferred or weakened.


## Owner Policy Gate Resolved (2026-11-18)

The owner explicitly approved the policy recorded in [F13](../features/F13-marketplace-business-integrity.md#approved-owner-policies-2026-11-18). This ticket's implementation has not started in the AP-0050 increment. It remains a separate reviewed implementation increment; policy approval alone is not completion.

## Implementation evidence

Shared explicit type/channel mapping and defaults now gate direct/durable/retried SSE, replay, legacy per-subscriber broadcast and every FCM token attempt/retry. Current snapshot semantics permit completion of already in-flight sends; no admin bypass. V15 adds lease-guarded atomic SUPPRESSED outcome plus attempt, excluded from claims/stats/replay. Lookup failures remain durable pending with existing bounded exhaustion; direct FCM retains bounded retries/result reporting, without a new queue. Authentication OTP and content-free heartbeats remain independent. Focused verification passed 71 tests across preferences, SSE, replay, broadcast, FCM, PostgreSQL leases/suppression/outages and schema. All 44 Mermaid diagrams parse. Additional stored-policy matrix is included in final verification. Final full suite and independent review remain pending.


### Replay review correction (2026-11-18)

Independent review found that a failed preference lookup could skip an unresolved
DISPATCHED event, emit a later replay/live event and advance the client's cursor.
The correction propagates replay lookup/storage failures to terminate the whole
stream; only explicit opt-outs intentionally skip rows. Live content is held
until replay completes, bounded to 100 queued events plus one held event;
overflow terminates the stream. Cancellation and replacement discard held content
and cancel replay, while content-free heartbeats remain independent. Reconnect
uses the last received cursor; stable IDs, bounded replay and client deduplication
retain their existing at-least-once contract, without global ordering guarantees.

Regression coverage includes failing A/allowed B, original-cursor recovery,
intentional opt-out, delayed success/error, buffer overflow, cancellation,
replacement and heartbeat lifecycle. The coordinator confirmed the new regression
suite failed against the original implementation (13 tests, seven failures, no
compilation errors). Corrected targeted verification passed 36 tests, including
active replay cancellation on overflow and live-source failure, plus existing SSE
compatibility checks. Full verification and renewed independent review are pending.
The earlier full-suite evidence applies only to the prior candidate.


### External review corrections (2026-11-21)

The six confirmed findings are addressed by downstream replay-before-live
serialization under limited demand; just-in-time single-row claims with unique
fencing and guarded post-policy renewal; UUID-value ownership checks and canonical
routing; provider-only Firebase circuit breaking with terminal ineligibility and
retry eligibility checks; shared category validation with HTTP 400; and explicit
suppression totals/terminal responses. Existing bounded buffers, cancellation,
replacement, heartbeats and at-least-once semantics remain required.

Coordinator RED evidence: the initial focused unit/HTTP set compiled and ran 35
tests with 15 expected failures and zero errors before production changes. The
lease PostgreSQL baseline RED was not run because Docker was unavailable at that
point; its controlled two-recovery regression covers both separate services and
overlapping work on the same instance. Corrected focused verification passed:
116 tests, zero failures/errors/skips across 12 affected test classes, including
23 PostgreSQL tests. All 45 Mermaid diagrams parsed successfully. Full clean
verification and fresh independent exact-candidate approvals remain pending.
Prior approvals are withdrawn; no completion or publication is claimed here.
