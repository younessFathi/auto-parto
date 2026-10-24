# AP-0030 Enforce Application and Persistence Boundaries

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Platform and Data](../features/F01-platform-and-data.md)
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md), [AP-0025](AP-0025-api-error-contract.md)
- **Source:** `architecture.md` Package Responsibilities and PostgreSQL Design

## Description
Keep transport, business logic, API contracts, mapping, and database-specific behavior in their defined architectural boundaries.

The public route catalog is documented in [`docs/api-boundaries.md`](../../docs/api-boundaries.md).
Versioned `/api/v1` routes are canonical for marketplace, file, notification, profile, buyer,
seller, and nested wallet operations. Existing legacy aliases have an explicit sunset and retain
the same security and DTO contract during migration. The obsolete local folder image route is
retired. The former PieceService update method was removed because it validated image IDs and
returned the existing record without persisting an update; file associations now use the upload
lifecycle.

## Acceptance Criteria
1. Controllers validate transport input, obtain the principal, call application services, and map responses without containing business or persistence logic.
2. API boundaries use DTOs and never expose relational persistence entities or internal numeric IDs.
3. Existing entity/DTO conversion patterns use MapStruct rather than duplicated manual mapping.
4. SQL and database-specific behavior remain in repositories.
5. Production query patterns are bounded, indexed, and verified with representative query plans.
6. JSONB is used only for genuinely flexible attributes, not as a substitute for relational fields.
7. Canonical public routes use `/api/v1`, legacy aliases have documented sunset dates, and retired routes have compatibility tests.

## Test Scenarios
1. **Given** an API endpoint, **when** its contract is tested, **then** only DTO fields are serialized and principal identity reaches the service.
2. **Given** a production query pattern, **when** its query plan is inspected, **then** the intended index is used within documented bounds.
3. **Given** a proposed flexible field, **when** schema review runs, **then** JSONB use requires documented variability that cannot be modeled relationally.

## AP-0001 Review Follow-up

Complete the API/URL public-ID contract: expose only entity UUIDs, map UUIDs to internal numeric IDs inside service/repository boundaries, and add controller tests proving internal IDs are never serialized or accepted as public identifiers.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated or repeatably verified and passing, architecture checks and documentation are complete, and all applicable suites pass.
