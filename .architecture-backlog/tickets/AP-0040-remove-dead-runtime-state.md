# AP-0040 Remove Dead Runtime State and Helpers

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Runtime Cleanup](../features/F12-runtime-cleanup.md)
- **Linked Tickets:** [AP-0037](AP-0037-remove-mongodb.md), [AP-0038](AP-0038-provider-neutral-device-tokens.md), [AP-0023](AP-0023-telemetry-health.md)

## Description

Remove verified dead runtime state and obsolete helpers that obscure the PostgreSQL-authoritative production architecture. Keep the change incremental: every removal must have a confirmed replacement path and focused regression coverage.

## Scope

1. Remove the unused JVM-local device-token map from `DeviceTokenService`; PostgreSQL remains authoritative and Redis remains a rebuildable projection.
2. Remove helpers and imports that have no callers after the map removal.
3. Search for additional candidates only after each bounded cleanup increment, and do not remove active MongoDB compatibility paths under this ticket; AP-0037 owns that migration.
4. Preserve token ownership, invalidation ordering, Redis recovery, authorization, and provider behavior.

## Acceptance Criteria

1. No authoritative or unused JVM-local device-token collection remains.
2. Token invalidation updates PostgreSQL first and performs best-effort Redis cleanup exactly as before.
3. Active-token reads and bounded Redis projection recovery continue to use the existing PostgreSQL and Redis boundaries.
4. Unit and PostgreSQL/Redis integration tests cover the affected behavior.
5. `git diff --check`, `./mvnw -B test`, and applicable container tests pass.
6. A sanitized `.agent-trace/` record describes the cleanup without sensitive values.

## Implementation Tracking

- Removed the unused `DeviceTokenService.userTokens` map and its unreferenced device-comparison helper. Invalidation now uses the authoritative PostgreSQL path followed by best-effort Redis cleanup, with the Redis-only compatibility path preserved until AP-0037/AP-0038 complete their separate contracts.
