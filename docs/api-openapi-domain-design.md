# Domain-Separated OpenAPI Design

**Design status:** Proposed for review under AP-0049
**Implementation:** Deferred to AP-0048 after this design is accepted
**Contract version:** `/api/v1` (with explicitly documented compatibility exceptions)

## Goal

Give administrators one Swagger UI with a selector for focused domain APIs, and provide a generated YAML specification file for each domain. Keep operation documentation out of controller method bodies, preserve the current application JWT and endpoint authorization, and avoid a second hand-maintained copy of the API contract.

## Proposed Source of Truth

Use Java application code as the single authoritative contract. Existing controller mappings and method signatures define executable paths and DTO types; validation annotations and the existing security rules define input constraints and access behavior. Small Java API contract interfaces implemented by controllers carry the operation descriptions and response/schema/security documentation metadata. Match interface methods to controller methods and do not repeat OpenAPI annotations in both places. The interface is a documentation contract, not a second implementation of route behavior.

Springdoc 2.8.4 is the project's current dependency. Its Spring Boot 3 / springdoc v2 documentation says annotations from interfaces are supported. It also documents grouped APIs, Swagger UI selection of multiple specs, and YAML output. AP-0048 must prove all of these behaviors in the project's exact WebFlux test context before rollout. [springdoc v2 documentation](https://springdoc.org/v2/)

The domain YAML files are generated outputs, not separately edited source contracts. Build/runtime groups are generated from Spring mappings, method/DTO signatures, validation/security configuration, and the API contract interfaces. This keeps the YAML files and Swagger UI aligned with the operations actually mounted by the application. Never make a manual YAML edit the only place an operation or security rule exists.

## Domain Groups and Route Ownership

| Group | Canonical routes | Compatibility and scope |
| --- | --- | --- |
| `identity` | `/auth/send-otp-email`, `/auth/validate-otp`, `/.well-known/jwks.json` | Authentication/discovery paths are explicit unversioned exceptions; include no OTP examples or credentials. |
| `marketplace` | `/api/v1/part-requests/**`, `/api/v1/part-listings/**`, `/api/v1/offers/**`, `/api/v1/buyers/**`, seller create/read/deactivate operations | Include `/offers`, `/customers/{publicId}`, and deprecated read-only `/pieces/**` with deprecation and the documented 2027-04-01 sunset. |
| `profiles` | `/api/v1/profiles/buyer`, `/api/v1/profiles/seller` | Include `/customers/profile` and `/seller/profile` only as deprecated aliases with the same sunset. |
| `wallets` | `/api/v1/sellers/{publicId}/wallet/**` | Public IDs, money amounts, currency, authorization, and safe errors must match actual DTO and service contracts. `/seller/{publicId}/wallet/**` is a deprecated alias until the documented sunset. |
| `files` | `/api/v1/files/**` | `/images` and local folder browsing are retired and must not appear as active operations. Upload ownership and UUID file-reference semantics remain explicit. |
| `notifications` | `/api/v1/notifications/**` | Include preferences, FCM, admin notification commands, and the canonical SSE stream. Deprecated `/notifications/**` aliases are clearly marked through their sunset. Never include tokens or message payloads as examples. |
| `admin-audit` | `/api/v1/admin/audit/**` | Explicitly label as administrator-only operational actions. The docs page itself remains admin-only; the endpoint retains its independent `ROLE_ADMIN` guard. |

This is an initial ownership map derived from the current controller mappings and `docs/api-boundaries.md`. During implementation, reconcile every route and method against the controller/security integration tests. In particular, AP-0048 must resolve any route-catalog omissions or ambiguity in path ID types before publishing a contract; do not infer a public UUID where the current route behavior has not proven one.

## Repository and Generated File Layout

Keep interface sources alongside the API contract package, grouped by resource/domain rather than one interface per whole feature or one catch-all interface:

```text
src/main/java/auto/parto/api/contract/
  identity/IdentityApi.java
  marketplace/PartRequestApi.java
  marketplace/PartListingApi.java
  marketplace/OfferApi.java
  profile/BuyerProfileApi.java
  profile/SellerProfileApi.java
  wallet/WalletApi.java
  file/FileApi.java
  notification/NotificationApi.java
  admin/AuditReplayApi.java
```

Controllers implement the relevant interface. Spring request mappings and method security remain at the existing controller boundary unless the implementation proves that moving a mapping is needed and preserves current route compatibility. Add no generated implementation code or hand-maintained parallel schema files unless AP-0049 is revised with a source-of-truth decision.

Generate one YAML output per group, named `target/generated-openapi/v1/<group>.yaml`, and expose the same group from the admin Swagger UI. Each YAML output represents the full operation contract for that group. Shared schemas/security/error definitions are consistently emitted from the shared Java DTOs and OpenAPI configuration; avoid copied component definitions that can diverge. The implementation must establish a repeatable exporter/test task against the running WebFlux application context, since the build must not check in stale generated specifications.

## UI and Security

- Swagger UI offers the seven named groups above; the YAML output for each group is separately retrievable for client tooling.
- Swagger UI, group JSON/YAML endpoints, configuration, redirects, and static assets use the existing application JWT and require canonical `ROLE_ADMIN` as defined by AP-0048.
- The UI uses the existing bearer JWT security scheme for “Try it out” and does not persist the bearer token in browser storage.
- The spec describes each operation's own authentication requirement and expected role/ownership rules. Admin permission to view the documentation does not elevate calls made through the UI.
- Do not expose actuator, migration, private deployment, provider credential, or other internal-only information in any group.
- A disabled profile must make both UI and every grouped JSON/YAML endpoint unavailable; no endpoint may become anonymous as a fallback.

## Validation and CI

AP-0048 should add focused checks that:

1. Start the application's WebFlux context and retrieve every named group in JSON and YAML.
2. Parse every generated YAML file and validate OpenAPI structure and all local/external references.
3. Assert every supported canonical HTTP method/path appears exactly once in its owner group and no operation appears in multiple groups.
4. Assert operation IDs are globally unique; shared component names and schemas are stable; undocumented controller operations fail the route-catalog check.
5. Compare paths, method, request DTO, response DTO, validation, authentication scheme, documented roles, public UUID semantics, error responses, and deprecation sunset against controller mappings, DTO validation, security tests, and `docs/api-boundaries.md`.
6. Fail if retired routes (including local folder browsing) are advertised as active, or if active compatibility routes lack a deprecation marker/sunset.
7. Verify anonymous and non-admin clients cannot fetch any Swagger configuration, group JSON/YAML, or UI assets, while a valid admin can.
8. Run through the existing Maven test/verify and GitHub workflow checks; document the generated YAML path for local development without publishing credentials.

The `target/generated-openapi` directory is disposable build output. It is not checked in and does not replace review of the interface/controller contract. If a future client needs a published static contract, publish the exact generated files from the verified build, keyed by application commit and API version.

## Incremental Implementation Order

1. Accept this design and reconcile the route catalog with actual controllers and authorization tests.
2. Add a small proof of concept for one WebFlux resource (part requests) with an interface implemented by the existing controller; verify inherited annotations, group generation, YAML export, shared schemas, and admin security.
3. Add the remaining domain interfaces and route groups incrementally; preserve all existing paths and compatibility dispositions.
4. Add the route/contract drift tests and admin-only Swagger security tests.
5. Document local admin access and generated YAML retrieval; only then proceed to AP-0021 Cloudflare/origin deployment validation.

## Deferred Decisions

- Whether auth/JWKS are in the admin Swagger UI or in a separately named identity group is proposed above and needs acceptance with the route catalog.
- Whether YAML outputs need to be retained as release attachments or only generated locally/CI is not a runtime decision; the default is build output only until a consumer requires distribution.
- Any future move to authored YAML as the source of truth would replace interface annotations and should generate controller API interfaces from that YAML. Do not run both authoring models simultaneously.
