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
| `files` | `POST /api/v1/files/requests/{requestId}`; `POST /api/v1/files/listings/{listingId}`; `GET /api/v1/files/{fileId}` | Uploads require the corresponding buyer/seller role and resource ownership. Reads require authentication and return only ACTIVE files; current reads have no caller ownership or buyer/seller-role restriction. UUID file-reference semantics remain explicit; `/images` and folder browsing are retired. |
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

- Swagger UI offers the seven named groups; each group has protected JSON/YAML and a safe YAML download.
- HTML, Swagger configuration, assets, group specifications and configured documentation aliases require the existing validated application JWT and canonical `ROLE_ADMIN` under AP-0048. Direct address-bar navigation without the launcher intentionally receives 401; Swagger Authorize cannot bootstrap a page that has not loaded.
- The application keeps its existing bearer authentication and endpoint role/ownership rules. Do not introduce an application auth cookie, Swagger password, anonymous asset exception or second authentication model.
- “Try it out” uses a token explicitly entered into Swagger Authorize, held in page memory with `persistAuthorization: false`. It is separate from the launcher's documentation admin token; documentation access does not authorize business calls.
- Do not expose actuator, migration, provider credentials, private deployment details or other internal-only information in any group. Disabled profiles make UI and all group/configuration endpoints unavailable, never anonymous.

### Administrator provisioning and acquisition prerequisite

Current public OTP strategies issue BUYER/SELLER credentials only; accepting `ROLE_ADMIN` during JWT validation does not provide an admin issuance flow. Do not tell an operator to obtain an admin token from public OTP. AP-0048 must implement an explicitly owner-approved, protected operator-only one-shot provisioning/issuance utility before manual launcher acceptance. No such utility or executable command is implemented by this design ticket. If approved provisioning or issuance is unavailable, AP-0048 completion and operator-access release are blocked; AP-0021 remains subsequent and is not an acquisition prerequisite.

Under authorized operator administration, the proposed utility provisions a dedicated canonical `app_users` ADMIN identity through existing audited repository paths (`AppUserRepository` / `R2dbcAppUserRepository`), preserving schema and audit-record constraints. It must not expose public signup/OTP role escalation or silently promote a buyer/seller. Before each issuance it rechecks the authorized provisioned identity's public UUID, email, `role=ADMIN` and `status=ACTIVE`, rejecting unapproved, non-admin or inactive identities. Use existing `JwtTokenGenerator` signing algorithms/key configuration from the approved protected server secret source, with a bounded configured expiry and the existing subject/principal-ID, email and `ROLE_ADMIN` claims. This is controlled issuance of the existing application credential, not a new password, authentication scheme or public endpoint.

Signing keys remain in the protected issuer/server process, never in the browser, CLI arguments or logs. Prefer an integrated one-shot issuer-to-launcher handoff through process memory or a private pipe, never display tokens on a terminal or save them to logs, files or environment variables; do not capture or log the private credential pipe. The launcher may also accept a legitimately pre-issued credential through a no-echo terminal prompt. Neither handoff persists credentials; the existing origin/path/redirect isolation and cleanup rules below apply equally to both.

Local/CI browser tests may use synthetic canonical ADMIN fixture identities and JWTs signed only with ephemeral or configured TEST keys, with the application context using the matching verification key. Never use real deployment signing keys/tokens in fixtures. Fixtures prove browser/security mechanics, not operational provisioning or acquisition. Real local/operator or isolated staging acceptance must use an approved provisioned admin and the controlled issuer; synthetic fixture success cannot substitute for that proof. AP-0048 adds issuance-denial tests for non-admin/inactive/unapproved identities, tests that public OTP cannot escalate roles, and checks that keys/tokens are absent from artifacts.

### Proposed protected-browser bootstrap (AP-0048 implementation)

AP-0048 will supply a local operator launcher using headed Playwright and a new nonpersistent browser context with `serviceWorkers: 'block'`. This helper, controlled issuer and browser tests are proposed deliverables, not implemented by AP-0049. Acquire its admin JWT only through the approved prerequisite above: prefer the integrated process-memory/private-pipe handoff, or accept a legitimately pre-issued token through a no-echo prompt. Hold it only in launcher memory: never put it in a URL, command-line argument, environment variable, file, browser storage, global extra HTTP headers, trace, HAR, video or log. Disable credential-bearing diagnostic capture. Closing the context discards its in-memory browser state.

The launcher accepts only an operator-trusted exact application origin: HTTPS, except explicitly selected loopback HTTP for local development. Trust comes from operator configuration, not a target supplied by a page or redirect. Serve trusted packaged Swagger assets from that same origin; use no CDN or external resources. Reject every cross-origin browser request/resource, regardless of path. Install scoped interception before the first navigation to the canonical `/swagger-ui/index.html`.

The documentation admin token is injected only for GET/HEAD on that exact origin and an explicit documentation-path allowlist: `/swagger-ui.html`, boundary-matched `/swagger-ui/**` and `/webjars/**`, the `/v3/api-docs` root, and the exact JSON/YAML, seven-group, configuration and configured-alias URLs verified against the actual springdoc configuration by AP-0048. Match parsed origin and pathname with segment boundaries; do not use arbitrary prefixes, lookalike paths or attacker-controlled targets. Non-allowlisted same-origin requests never receive the documentation token.

For an allowlisted documentation request, the handler uses `route.fetch({ headers, maxRedirects: 0 })` with the scoped Authorization header. It fulfills only a nonredirect response. Abort every 3xx without following `Location` or forwarding that response to the browser, including same-origin redirects; fail closed and require a canonical nonredirecting URL. Never inject this token via `route.continue({ headers })` or automatically follow redirects: modified headers can otherwise be carried across redirect hops. AP-0048 must prove the complete canonical HTML, asset, configuration, group and download chain requires no redirect. [Playwright Route.fetch documents fetch/fulfill and `maxRedirects: 0`](https://playwright.dev/docs/api/class-route#route-fetch).

Same-origin non-document API calls proceed normally without injected documentation credentials; preserve any Swagger Authorize token explicitly entered for that call. Do not proxy/buffer ordinary API or SSE traffic through `route.fetch`: streaming must retain normal behavior. Test protected API calls without an entered token as 401, with allowed buyer/seller tokens under their actual endpoint contracts, and with insufficient role/ownership as the normal denial. Block service workers so they cannot bypass interception; this complements scoped interception and explicit redirect refusal rather than replacing either control. [Playwright network guidance on service workers](https://playwright.dev/docs/network#missing-network-events-and-service-workers).

On a documentation 401/403, stop injecting, close/discard the context and token references, and require relaunch with a newly validated admin JWT. Explicit launcher close or Ctrl+C is local logout with the same cleanup; it does not globally revoke a server JWT. No token is persisted to resume later. The operator may explicitly save only a chosen generated YAML output after validation that it contains no secrets or sensitive examples; do not save browser state or credential-bearing artifacts.

## Validation and CI

AP-0048 should add focused checks that:

1. Start the application's WebFlux context and retrieve every named group in JSON and YAML.
2. Parse every generated YAML file and validate OpenAPI structure and all local/external references.
3. Assert every supported canonical HTTP method/path appears exactly once in its owner group and no operation appears in multiple groups.
4. Assert operation IDs are globally unique; shared component names and schemas are stable; undocumented controller operations fail the route-catalog check.
5. Compare paths, method, request DTO, response DTO, validation, authentication scheme, documented roles, public UUID semantics, error responses, and deprecation sunset against controller mappings, DTO validation, security tests, and `docs/api-boundaries.md`.
6. Fail if retired routes (including local folder browsing) are advertised as active, or if active compatibility routes lack a deprecation marker/sunset.
7. Verify anonymous, malformed/expired-token and authenticated non-admin clients cannot fetch HTML, assets, configuration, every group JSON/YAML or configured alias. Disabled-profile requests remain unavailable. A plain fresh browser without the launcher intentionally receives 401.
8. Exercise the proposed launcher in a fresh nonpersistent headed context: first canonical HTML navigation, all packaged assets/configuration, selection of all seven groups in JSON/YAML, and an explicitly chosen safe YAML download must succeed without redirects for a validated admin. Assert no credential persistence in URLs, arguments, environment, browser storage or diagnostics.
9. Prove origin/path boundaries, cross-origin request/resource blocking, rejection of lookalike or unrelated documentation paths, refusal of every documentation 3xx without following/forwarding it, and no token leakage to redirect targets. Confirm same-origin non-document API calls never inherit the documentation admin token: protected API without Swagger authorization is 401, while entered buyer/seller tokens follow endpoint roles/ownership and SSE remains streaming.
10. Exercise documentation-token expiry/invalidity and 401/403 cleanup, explicit close/Ctrl+C local logout, and successful relaunch with a newly controlled-issued JWT; do not imply global token revocation. Separately verify issuance rejects unapproved/non-admin/inactive identities, public OTP cannot issue ADMIN, and tokens/signing keys never appear in artifacts. Use TEST-only fixture keys/identities for local/CI mechanics, then obtain real local/operator or isolated staging acquisition proof through the approved utility; do not equate fixtures with operational acceptance.
11. Run through existing Maven test/verify and GitHub checks. AP-0048 completes application/browser/group and real operator acquisition acceptance locally or in isolated staging without waiting for Cloudflare; it cannot complete without approved provisioning/issuance. AP-0021 subsequently establishes the edge and owns equivalent through-edge authorization/bootstrap regressions before public release. Document generated YAML retrieval without publishing credentials.

The `target/generated-openapi` directory is disposable build output. It is not checked in and does not replace review of the interface/controller contract. If a future client needs a published static contract, publish the exact generated files from the verified build, keyed by application commit and API version.

## Developer Workflow

1. When adding or changing an API operation, update the controller mapping, DTO/validation, role and ownership behavior, and the matching small API contract interface. Keep the interface method signature aligned with the implemented controller method.
2. Add or update the operation description, response codes, and schema annotations on the interface. Do not place a second copy of the OpenAPI annotations on the controller method.
3. Update `docs/api-boundaries.md` with the canonical method/path, owner group, compatibility disposition, and sunset where relevant.
4. Run `./mvnw -B test` and `./mvnw -B verify` in a Docker-enabled environment. The AP-0048 contract exporter/tests generate one YAML file per group under `target/generated-openapi/v1/` and fail on unmatched routes, invalid references, or incorrect UI/security behavior.
5. Once AP-0048 implements the explicitly approved provisioning/issuance utility and launcher, provision the dedicated canonical admin under authorized operator administration and revalidate it for controlled issuance. Select the trusted local/isolated target and hand the JWT to the launcher through process memory/private pipe, or enter a legitimately pre-issued credential at its no-echo prompt. Select groups and explicitly save only safe generated YAML. Enter a separate appropriate business token into Swagger Authorize for protected API calls. Close the launcher for local logout; direct address-bar navigation remains 401. Ordinary buyer/seller OTP does not supply an admin token, and TEST fixtures do not establish operator acceptance. No issuer, launcher or runtime security is implemented by AP-0049.

## Incremental Implementation Order

1. Accept this design and preserve the operation inventory in `docs/api-boundaries.md` as the catalog against which controller mappings and authorization tests are checked.
2. Add a small proof of concept for one WebFlux resource (part requests) with an interface implemented by the existing controller; verify inherited annotations, group generation, YAML export, shared schemas, and admin security.
3. Add the remaining domain interfaces and route groups incrementally; preserve all existing paths and compatibility dispositions.
4. Obtain explicit owner approval for and implement AP-0048's protected one-shot admin provisioning/issuance utility before manual launcher acceptance. Implement the launcher, fresh-browser/bootstrap, token isolation/cleanup, redirect refusal, issuance/security and contract-drift tests. TEST-only fixtures may verify mechanics, but complete real operator acquisition and application/group/YAML acceptance locally or in isolated staging using the approved utility. Without that acquisition proof AP-0048 remains blocked; Cloudflare is not its prerequisite.
5. After accepted AP-0048 application proof, AP-0021 establishes Cloudflare/origin routing and performs through-edge browser, authorization, YAML and SSE regressions before public release. Record this later edge proof under AP-0021 rather than reopening a circular AP-0048 prerequisite.

## Design Decision Proposal

- Include authentication/JWKS operations in the named `identity` group; keep the OpenAPI browsing surface itself admin-only.
- Generate YAML per domain into disposable `target/generated-openapi/v1` outputs and serve the same groups through protected springdoc endpoints. Do not commit generated YAML or publish it externally until a consumer/release policy requires it.
- Keep Java controller/API contract sources authoritative and use documentation interfaces implemented by controllers. A future switch to authored YAML as the source of truth requires a new decision and generated controller interfaces; do not operate both source models simultaneously.
