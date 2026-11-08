# Domain-Separated OpenAPI Design

**Design status:** Complete proposal, awaiting owner approval under AP-0049
**Implementation:** Deferred to AP-0048 after this design is accepted
**Contract version:** `/api/v1` (with explicitly documented compatibility exceptions)

## Goal

Give administrators one Swagger UI with a selector for focused domain APIs, and provide a generated YAML specification file for each domain. Keep operation documentation out of controller method bodies, preserve the current application JWT and endpoint authorization, and avoid a second hand-maintained copy of the API contract.

## Contract Source of Truth and YAML Generation

Use Java application code as the single authoritative contract. Existing controller mappings and method signatures define executable paths and DTO types; validation annotations and the existing security rules define input constraints and access behavior. Small Java API contract interfaces implemented by controllers carry the operation descriptions and response/schema/security documentation metadata. Match interface methods to controller methods and do not repeat OpenAPI annotations in both places. The interface is a documentation contract, not a second implementation of route behavior.

Springdoc 2.8.4 is the project's current dependency. Its Spring Boot 3 / springdoc v2 documentation says annotations from interfaces are supported. It also documents grouped APIs, Swagger UI selection of multiple specs, and YAML output. AP-0048 must prove all of these behaviors in the project's exact WebFlux test context before rollout. [springdoc v2 documentation](https://springdoc.org/v2/)

Generate the per-domain YAML outputs from Spring mappings, method/DTO signatures, validation/security configuration, and the API contract interfaces. The outputs are separate YAML files for inspection and tooling, but are not hand-edited source contracts. This keeps YAML and Swagger UI aligned with operations actually mounted by the application. Never make a manual YAML edit the only place an operation or security rule exists.

Use `GroupedOpenApi` path filters to create one runtime group for each domain. Springdoc serves each group in JSON and YAML; configure Swagger UI's selector with the group URLs. A repeatable Maven-bound exporter/integration test retrieves each YAML representation from the application context and writes `target/generated-openapi/v1/<group>.yaml`. Verify the exporter with the project's exact dependency before relying on a specific plugin hook. Build outputs are disposable and are not committed. A later release may publish the exact generated files keyed to the application commit if consumers need downloads. [springdoc documents grouped APIs and YAML output](https://springdoc.org/v2/)

## Domain Groups and Route Ownership

| Group | Canonical routes | Compatibility and scope |
| --- | --- | --- |
| `identity` | `/auth/send-otp-email`, `/auth/validate-otp`, `/.well-known/jwks.json` | Authentication/discovery paths are explicit unversioned exceptions; include no OTP examples or credentials. |
| `marketplace` | Exact operations listed under `marketplace` in [`docs/api-boundaries.md`](api-boundaries.md) | Includes requests, matching, listings, offers, buyer deactivation, seller create/read/deactivation, and deprecated read-only `/pieces/**`; the catalog lists each method/path and alias. |
| `profiles` | `PUT /api/v1/profiles/buyer`; `PUT /api/v1/profiles/seller` | Includes deprecated `/customers/profile` and `/seller/profile` aliases with the same sunset. |
| `wallets` | `POST /api/v1/sellers/{id}/wallet/add`; `POST /api/v1/sellers/{id}/wallet/deduct` | Amount/currency, idempotency, authorization, and error behavior follow actual DTO/service contracts. `/seller/{id}/wallet/**` aliases sunset 2027-04-01. |
| `files` | `POST /api/v1/files/requests/{requestId}`; `POST /api/v1/files/listings/{listingId}`; `GET /api/v1/files/{fileId}` | `/images` and local folder browsing are retired and absent. Upload ownership and UUID file-reference semantics remain explicit. |
| `notifications` | Exact operations listed under `notifications` in [`docs/api-boundaries.md`](api-boundaries.md) | Includes preferences, FCM, admin commands, and canonical SSE. Deprecated `/notifications/**` aliases are clearly marked through their sunset. Never include tokens or message payloads as examples. |
| `admin-audit` | `POST /api/v1/admin/audit/{eventId}/replay` | Administrator-only operational action. The docs page is separately admin-only; the endpoint retains its independent `ROLE_ADMIN` guard. |

The operation inventory in `docs/api-boundaries.md` was reconciled against every controller mapping. The catalog records UUID path semantics only when controller/service behavior validates them; FCM stats remains a canonical-principal string until its format has a dedicated contract test. AP-0048 must preserve a generated-route-vs-catalog test so later controller changes cannot silently invalidate this design.

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

Controllers implement the relevant interface. Spring request mappings and method security remain at the existing controller boundary unless the implementation proves that moving a mapping is needed and preserves current route compatibility. Do not generate server implementation code or maintain parallel hand-authored schema files.

Generate one YAML output per group, named `target/generated-openapi/v1/<group>.yaml`, and expose the same group from the admin Swagger UI. Each file is a standalone OpenAPI document. Shared Java DTOs and common security/error definitions are emitted as components in each document, with operations referring to local `#/components/schemas/...` references. Do not use cross-file `$ref`s; each YAML can then be downloaded and validated independently while common definitions still derive from one Java source.

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

## Developer Workflow

1. When adding or changing an API operation, update the controller mapping, DTO/validation, role and ownership behavior, and the matching small API contract interface. Keep the interface method signature aligned with the implemented controller method.
2. Add or update the operation description, response codes, and schema annotations on the interface. Do not place a second copy of the OpenAPI annotations on the controller method.
3. Update `docs/api-boundaries.md` with the canonical method/path, owner group, compatibility disposition, and sunset where relevant.
4. Run `./mvnw -B test` and `./mvnw -B verify` in a Docker-enabled environment. The AP-0048 contract exporter/tests generate one YAML file per group under `target/generated-openapi/v1/` and fail on unmatched routes, invalid references, or incorrect UI/security behavior.
5. Run the app locally and open the protected Swagger UI using a local administrator JWT. Select the domain group and download its YAML to inspect the generated contract. Do not store a real production token in browser storage or examples.

## Incremental Implementation Order

1. Accept this design and preserve the operation inventory in `docs/api-boundaries.md` as the catalog against which controller mappings and authorization tests are checked.
2. Add a small proof of concept for one WebFlux resource (part requests) with an interface implemented by the existing controller; verify inherited annotations, group generation, YAML export, shared schemas, and admin security.
3. Add the remaining domain interfaces and route groups incrementally; preserve all existing paths and compatibility dispositions.
4. Add the route/contract drift tests and admin-only Swagger security tests.
5. Document local admin access and generated YAML retrieval; only then proceed to AP-0021 Cloudflare/origin deployment validation.

## Design Decision Proposal

- Include authentication/JWKS operations in the named `identity` group; keep the OpenAPI browsing surface itself admin-only.
- Generate YAML per domain into disposable `target/generated-openapi/v1` outputs and serve the same groups through protected springdoc endpoints. Do not commit generated YAML or publish it externally until a consumer/release policy requires it.
- Keep Java controller/API contract sources authoritative and use documentation interfaces implemented by controllers. A future switch to authored YAML as the source of truth requires a new decision and generated controller interfaces; do not operate both source models simultaneously.
