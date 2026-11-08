# AP-0049 Design Domain-Separated OpenAPI YAML Specifications

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Platform and Data](../features/F01-platform-and-data.md)
- **Linked Tickets:** [AP-0048 Admin-Protected Swagger](AP-0048-admin-protected-api-documentation.md), [AP-0030 Application Boundaries](AP-0030-application-boundaries.md)
- **Source:** `docs/api-boundaries.md`; `docs/architecture.md` Authentication and Authorization
- **Design Proposal:** [Domain-Separated OpenAPI Design](../../docs/api-openapi-domain-design.md)

## Description

Design how Auto-Parto will maintain and serve OpenAPI YAML specifications split by domain, before implementing the Swagger UI and adding domain specifications. The design must make it straightforward to understand each API area independently while preserving one accurate, versioned `/api/v1` contract and AP-0048's admin-only Swagger access.

The design must resolve the relationship between domain YAML files and AP-0048's separate Java API contract interfaces implemented by controllers. Select one authoritative source for each operation. Do not require engineers to maintain the same path, request/response schema, or security requirements independently in both YAML and Java annotations without automated drift detection.

## Design Deliverables

1. A reviewed design note that assigns each canonical route in `docs/api-boundaries.md` to one domain group, including shared concepts such as authentication, safe errors, file references, wallets, and notifications/SSE.
2. A proposed repository layout and naming/versioning convention for domain YAML outputs, shared schemas/responses/parameters/security definitions, and `$ref` behavior. Cover marketplace requests/listings/offers, profiles and identities, files, wallets, and notifications, and state whether cross-file references are used.
3. Select one source of truth and explain its tradeoffs. The completed proposal selects Java controller/API contract sources as authoritative and generates per-domain YAML outputs; it prohibits separately hand-maintained duplicate contracts and defines route/spec drift checks.
4. A Swagger UI grouping/aggregation design that lets an authorized administrator select each domain specification while keeping all UI, YAML, JSON, and supporting resource endpoints protected as required by AP-0048.
5. A repeatable validation plan for YAML syntax, `$ref` resolution, OpenAPI version/schema validity, duplicate operation IDs, duplicate/conflicting paths, canonical route coverage, DTO/error/security consistency, and legacy-route sunset handling. Identify the Maven/CI checks needed before merge.
6. A migration plan from current scattered Swagger annotations to the selected design. Preserve AP-0048's requirement to keep documentation out of controller method bodies by moving OpenAPI documentation to small contract interfaces implemented by controllers.
7. A short usage guide for adding or changing a domain endpoint, generating or validating its YAML, and seeing it in the protected Swagger UI. Use examples with no real credentials or personal data.

## Acceptance Criteria

1. The design covers every active canonical route in `docs/api-boundaries.md`, assigns each operation to exactly one domain group, and records how deprecated compatibility aliases and retired endpoints are represented.
2. The design chooses exactly one source of truth for operation paths, DTO schemas, responses, and security metadata, and defines automated checks to detect drift from controller behavior and API-boundary tracking.
3. The design defines reusable cross-domain components and `$ref` resolution without creating a monolithic all-domain document that must be edited for unrelated endpoint changes.
4. The design preserves `/api/v1`, public UUID rules, the existing bearer JWT model, canonical `ROLE_ADMIN` access to Swagger under AP-0048, endpoint-specific role/ownership enforcement, and safe error contracts.
5. The design specifies that no API spec group includes secrets, internal operational endpoints, private deployment details, or sensitive examples; any intentional admin operations are clearly labeled.
6. The design defines local developer workflow, Maven/CI validation, Swagger UI grouping, and a staged migration order that can be implemented incrementally without exposing anonymous documentation.
7. No runtime Swagger, controller, or security implementation is included in this design ticket. Implementation follows only after the design is reviewed and accepted; AP-0048 remains Proposed until its dependency is satisfied.

## Test Scenarios

1. **Given** the canonical API route catalog and controller mappings, **when** the design inventory is reviewed, **then** every active method/path has one owner domain and every compatibility/retired path has an explicit disposition.
2. **Given** shared DTOs or error schemas appear in multiple domain groups, **when** the design is reviewed, **then** each standalone YAML has consistent generated local components and resolvable local references, with no hand-maintained cross-file duplication.
3. **Given** a controller path, DTO, or role policy changes, **when** AP-0048's specified validation suite is implemented, **then** it will detect contract drift or missing domain-spec coverage before merge.
4. **Given** an unauthenticated or non-admin caller, **when** the design is checked against AP-0048, **then** it specifies denial for every grouped YAML/JSON spec and Swagger asset; AP-0048 remains responsible for runtime verification.

## Definition of Done

The domain ownership map, repository layout, source-of-truth decision, UI aggregation, validation strategy, migration plan, and usage guide are documented and self-reviewed. Every current controller route and legacy disposition is accounted for in `docs/api-boundaries.md`; AP-0048 is updated with the concrete implementation approach. The proposal is ready for owner acceptance. No runtime or security behavior is changed by this design ticket.
