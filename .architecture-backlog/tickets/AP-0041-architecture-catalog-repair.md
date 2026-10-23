# AP-0041 Repair the Architecture Catalog

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Platform and Data](../features/F01-platform-and-data.md)
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md), [AP-0033](AP-0033-github-actions-migration.md), [AP-0037](AP-0037-remove-mongodb.md), [AP-0040](AP-0040-remove-dead-runtime-state.md)

## Problem

The active architecture catalog still contained MongoDB-era runtime statements, stale CI language, missing feature-index files, and links to tickets that were never created. This made the implementation status and dependency graph misleading after the PostgreSQL cutover.

## Scope

1. Align the agent guide and architecture documentation with the PostgreSQL/R2DBC-only application runtime, Redis projections, and minor-unit money boundary.
2. Restore the F01 and F05 feature indexes and repair active links and status/dependency relationships across the catalog.
3. Retire absent AP-0014, AP-0015, and AP-0038 references explicitly where their work was absorbed by existing tickets.
4. Keep historical review, trace, and implementation records immutable; label historical MongoDB or GitLab references where they remain necessary for context.
5. Add a focused catalog test that catches broken active Markdown links and status drift.

## Acceptance Criteria

- [x] Active guide, architecture, feature, and ticket text describes PostgreSQL/R2DBC as the current application persistence runtime.
- [x] F01, F05, and F12 are linked from the backlog index and all active Markdown links resolve.
- [x] Missing ticket references are repaired or explicitly described as absorbed/retired.
- [x] F02, F03, F04, F06, F07, F08, F09, F12, AP-0025, AP-0030, AP-0033, and AP-0037 have current status and dependency text, including explicit follow-up ownership for unfinished work.
- [x] Historical reviews and traces are unchanged.
- [x] `ArchitectureCatalogTest` covers active link resolution and the reconciled status set.
- [x] `git diff --check` and the focused catalog test pass.

## Implementation Tracking

- Updated `AGENTS.md`, `docs/architecture.md`, and active backlog records to describe the PostgreSQL-only runtime and current GitHub Actions path.
- Restored the F01 and F05 feature indexes, added F12 to the root index, and removed links to absent AP-0003/AP-0014/AP-0015/AP-0038 tickets. Absorbed responsibilities are recorded in the relevant feature/ticket entries.
- Verification: `git diff --check` and `./mvnw -B -Dtest=ArchitectureCatalogTest test`.

## Definition of Done

The active architecture catalog is internally linked, status text matches the implemented runtime, historical records remain immutable, and the focused regression test passes.
