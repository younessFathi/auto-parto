# AP-0049 Design Domain-Separated OpenAPI YAML Specifications

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Platform and Data](../features/F01-platform-and-data.md)
- **Linked Tickets:** [AP-0048 Admin-Protected Swagger](AP-0048-admin-protected-api-documentation.md), [AP-0030 Application Boundaries](AP-0030-application-boundaries.md)
- **Source:** `docs/api-boundaries.md`; `docs/architecture.md` Authentication and Authorization
- **Design Draft:** [Domain-Separated OpenAPI Design](../../docs/api-openapi-domain-design.md)

## Description

Design how Auto-Parto will maintain and serve OpenAPI YAML specifications split by domain, before implementing the Swagger UI and adding domain specifications. The design must make it straightforward to understand each API area independently while preserving one accurate, versioned `/api/v1` contract and AP-0048's admin-only Swagger access.

The design must resolve the relationship between domain YAML files and AP-0048's separate Java API contract interfaces implemented by controllers. Select one authoritative source for each operation. Do not require engineers to maintain the same path, request/response schema, or security requirements independently in both YAML and Java annotations without automated drift detection.

## Design Deliverables

1. A reviewed design note that assigns each canonical route in `docs/api-boundaries.md` to one domain group, including shared concepts such as authentication, safe errors, file references, wallets, and notifications/SSE.
2. A proposed repository layout and naming/versioning convention for domain YAML files, shared schemas/responses/parameters/security definitions, and cross-file `$ref` relationships. Cover marketplace requests/listings/offers, profiles and identities, files, wallets, and notifications.
3. An explicit source-of-truth decision: either authored domain YAML with generated/validated UI aggregation, or generated per-domain YAML from the Java API contract interfaces. If another approach is chosen, document why it avoids duplicate contracts and how implementation drift is caught. The current design draft recommends interface annotations as the source and per-domain YAML as generated build outputs; this remains subject to owner review.
4. A Swagger UI grouping/aggregation design that lets an authorized administrator select each domain specification while keeping all UI, YAML, JSON, and supporting resource endpoints protected as required by AP-0048.
5. A repeatable validation plan for YAML syntax, `$ref` resolution, OpenAPI version/schema validity, duplicate operation IDs, duplicate/conflicting paths, canonical route coverage, DTO/error/security consistency, and legacy-route sunset handling. Identify the Maven/CI checks needed before merge.
6. A migration plan from current scattered Swagger annotations to the selected design. Preserve AP-0048's requirement to keep documentation out of controller method bodies and its interface-implementation preference unless the approved design demonstrates that generated YAML better satisfies it without duplicated contracts.
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

1. **Given** the canonical API route catalog, **when** the design is reviewed, **then** every route has one documented owner domain and every legacy/retired route has an explicit disposition.
2. **Given** two domains reference a shared DTO or error schema, **when** the YAML is assembled or generated, **then** references resolve to one consistent shared contract with no conflicting duplicate definitions.
3. **Given** an endpoint changes its path, DTO, or role policy, **when** the proposed validation suite runs, **then** it detects contract drift or missing domain-spec coverage before merge.
4. **Given** an unauthenticated or non-admin caller, **when** any grouped spec is requested through the proposed Swagger UI, **then** AP-0048's authentication and authorization rules still deny access.

## Definition of Done

The domain ownership map, repository layout, single-source-of-truth decision, UI aggregation, validation strategy, migration plan, and usage guide are documented and reviewed. Every current canonical route and legacy disposition is accounted for, and AP-0048 is updated to implement the accepted design. No runtime or security behavior is changed by this design ticket.
