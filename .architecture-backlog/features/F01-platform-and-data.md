# F01 Platform and Data

Provide the PostgreSQL-backed persistence, API contract, and application-boundary foundations used by every domain feature.

**Status:** In Progress

- [AP-0001](../tickets/AP-0001-postgresql-schema.md): Define the versioned PostgreSQL schema and integrity constraints. **Done**
- [AP-0002](../tickets/AP-0002-reactive-persistence.md): Establish reactive PostgreSQL persistence boundaries. **Done**
- [AP-0025](../tickets/AP-0025-api-error-contract.md): Standardize the safe API error contract. **Done**
- [AP-0030](../tickets/AP-0030-application-boundaries.md): Enforce application and persistence boundaries. **Proposed**
- [AP-0033](../tickets/AP-0033-github-actions-migration.md): Run the repository CI through GitHub Actions. **Done**

## Completion Tracking

The PostgreSQL schema, R2DBC repositories, safe error contract, and GitHub Actions workflow are implemented and verified. F01 remains in progress because the architectural boundary checks in AP-0030 are still proposed.
