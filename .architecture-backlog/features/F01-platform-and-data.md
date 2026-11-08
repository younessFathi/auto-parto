# F01 Platform and Data

Provide the PostgreSQL-backed persistence, API contract, and application-boundary foundations used by every domain feature.

**Status:** In Progress

- [AP-0001](../tickets/AP-0001-postgresql-schema.md): Define the versioned PostgreSQL schema and integrity constraints. **Done**
- [AP-0002](../tickets/AP-0002-reactive-persistence.md): Establish reactive PostgreSQL persistence boundaries. **Done**
- [AP-0025](../tickets/AP-0025-api-error-contract.md): Standardize the safe API error contract. **In Progress**
- [AP-0030](../tickets/AP-0030-application-boundaries.md): Enforce application and persistence boundaries. **Done**
- [AP-0033](../tickets/AP-0033-github-actions-migration.md): Run the repository CI through GitHub Actions. **In Progress**
- [AP-0048](../tickets/AP-0048-admin-protected-api-documentation.md): Provide administrator-only Swagger/OpenAPI documentation. **Proposed**
- [AP-0049](../tickets/AP-0049-design-domain-openapi-yaml.md): Design domain-separated OpenAPI YAML specifications and choose their authoritative source. **Proposed**

## Completion Tracking

The PostgreSQL schema, R2DBC repositories, and API boundary contract are implemented and verified. The safe error contract and CI migration remain in progress. Swagger/OpenAPI is present but currently permits anonymous access. AP-0049 is the design-first ticket for domain-separated YAML specifications and their source of truth; AP-0048 will implement admin-only access and the accepted API documentation design before AP-0021 deployment configuration.
