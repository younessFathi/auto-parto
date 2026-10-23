# AP-0001 Establish Versioned PostgreSQL Schema

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Platform and Data](../features/F01-platform-and-data.md)
- **Linked Tickets:** None
- **Source:** `architecture.md` PostgreSQL Design

## Description
Create the relational schema, migrations, constraints, indexes, timestamps, statuses, and concurrency fields for all target aggregates.

## Acceptance Criteria
1. Versioned migrations create users, profiles, requests, listings, offers, wallets, ledger, addresses, OTP challenges, files, device tokens, notifications, and outbox records.
2. Foreign keys, uniqueness, checks, and nullability enforce ownership and integrity.
3. Production never relies on automatic schema creation.
4. Indexes cover ownership, public-ID, state-processing, and search queries and are verified with representative query plans.
5. Each public entity has a unique UUID while internal `BIGINT` IDs remain confined to joins and repository/service boundaries.
6. Financial and security-sensitive records carry required immutable audit references.

## Test Scenarios
1. **Given** an empty database, **when** migrations run, **then** the complete schema is created.
2. **Given** invalid constrained data, **when** it is inserted, **then** PostgreSQL rejects it.
3. **Given** the current schema, **when** migrations rerun, **then** no duplicate objects are created.
4. **Given** an API response or URL, **when** an entity is referenced, **then** no internal numeric ID is exposed.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, migrations and documentation are complete, and all applicable test and verification suites pass.
