# AP-0048 Admin-Protected Swagger and OpenAPI Documentation

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Platform and Data](../features/F01-platform-and-data.md)
- **Linked Tickets:** [AP-0025 Safe API Error Contract](AP-0025-api-error-contract.md), [AP-0030 Application Boundaries](AP-0030-application-boundaries.md), [AP-0049 Domain OpenAPI YAML Design](AP-0049-design-domain-openapi-yaml.md), [AP-0021 TLS Entry Point](AP-0021-tls-entrypoint.md)
- **Source:** `docs/architecture.md` Authentication and Authorization; `docs/api-boundaries.md`

## Description

Provide an accurate Swagger UI and OpenAPI specification so administrators can inspect the application's current HTTP API. Restrict the UI, the generated specification, and any supporting Swagger resources to users authenticated with the existing application JWT and canonical `ROLE_ADMIN` principal. Authentication and role authorization must use the same security chain and canonical-principal rules as the rest of the API; do not create a separate Swagger password or authentication mechanism.

Swagger/OpenAPI is already on the classpath, but its paths are currently permitted anonymously and excluded from JWT processing. This ticket closes that access gap, documents the canonical `/api/v1` API accurately, and establishes Swagger as a prerequisite to AP-0021 edge configuration. Admin access to documentation does not grant access to the underlying business endpoints: every API request must continue to enforce its own role and ownership rules.

Keep OpenAPI documentation separate from controller implementation code. AP-0049 must first design the domain-separated YAML layout and choose one source of truth for operation contracts. Preserve the preference for small API contract interfaces implemented by controllers if the accepted design uses Java annotations; if domain YAML is authoritative, avoid maintaining duplicate operation metadata on the interfaces. Do not add a large block of documentation annotations to controller methods or create a single catch-all documentation interface. Keep runtime authorization and business behavior in the existing security/controller layers.

## Acceptance Criteria

1. Require a valid existing application JWT with canonical `ROLE_ADMIN` for `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/webjars/**`, and any configured alternate Swagger/OpenAPI path. Missing or invalid credentials receive the established 401 contract; authenticated non-admin roles receive 403; administrators can load both UI and specification.
2. Ensure Swagger requests are not bypassed by the JWT authentication filter or permitted by an earlier security matcher. Derive the role only from validated canonical authentication; never trust a request parameter, header, or OpenAPI UI input as an authorization claim.
3. Configure the UI to use the existing bearer JWT scheme for API calls. Do not persist bearer credentials in browser local storage or other durable browser storage. Swagger authorization must not alter or bypass per-endpoint authorization and ownership checks.
4. Document canonical `/api/v1` routes and their actual DTOs, validation constraints, response/error contracts, and authentication requirements. Reconcile legacy aliases with `docs/api-boundaries.md`: mark supported aliases deprecated with their sunset, or exclude retired routes. Do not advertise removed routes or imply that documentation grants broader access.
5. Implement the accepted AP-0049 domain YAML design and its single source of truth. Keep Swagger/OpenAPI documentation out of controller method bodies. If the design selects Java interface annotations, use small resource/controller API interfaces implemented by corresponding controllers and verify springdoc generates their documentation. If authored YAML is selected, serve/group the domain files and test them against actual controller route behavior without duplicating undocumented contracts.
6. Keep the OpenAPI specification free of secrets, internal credentials, private deployment details, and sensitive example data. Avoid exposing actuator, migration, or internal-only operations as public API documentation; if intentionally documented for administrators, label their operational scope clearly.
7. Add focused security/controller integration coverage for anonymous, malformed/expired-token, authenticated non-admin, and administrator access to both the UI and every domain specification. Add contract coverage that confirms route groups and the bearer scheme remain aligned with the canonical API catalog and that the accepted source of truth appears correctly in each domain spec.
8. Document the local administrator workflow without including real credentials. Keep the endpoints admin-protected in every enabled profile, or explicitly disable Swagger/OpenAPI in a profile through tested configuration; never fall back to anonymous access.
9. Complete AP-0048 and verify the chosen Cloudflare/origin deployment can pass authorized Swagger requests without widening public origin access before AP-0021 proceeds to deployment.

## Test Scenarios

1. **Given** an unauthenticated request, **when** it requests the Swagger UI, JSON specification, or supporting UI assets, **then** it receives the established 401 response and no specification content.
2. **Given** a valid buyer or seller JWT, **when** the user requests the UI or specification, **then** access is denied with 403 and no documentation body is returned.
3. **Given** a valid administrator JWT, **when** the user requests the UI and JSON specification, **then** both are available and the UI uses bearer authorization without persistent token storage.
4. **Given** an administrator viewing Swagger, **when** the UI calls an endpoint that requires another role or resource ownership, **then** that endpoint's normal authorization remains enforced.
5. **Given** canonical and legacy route changes, **when** OpenAPI contract tests run, **then** documented routes match `docs/api-boundaries.md` and removed routes are absent.
6. **Given** Swagger is disabled by an explicit profile setting, **when** any Swagger/OpenAPI path is requested, **then** it is unavailable and cannot become public through a security matcher.

## Definition of Done

AP-0049's design is accepted and Swagger/OpenAPI accurately describes the active API in domain groups, administrator-only wherever enabled. The existing JWT, canonical-principal role checks, safe error behavior, and per-endpoint authorization remain intact. Security, YAML/reference, and route-catalog tests pass; local usage and profile behavior are documented; AP-0021 records the dependency; and the applicable test and verification suites pass.
