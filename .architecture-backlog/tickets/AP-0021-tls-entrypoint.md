# AP-0021 Configure TLS Entry Point

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md), [AP-0028](AP-0028-provider-isolation.md), [AP-0046 VPS Infrastructure Automation](AP-0046-provision-vps-topology-with-opentofu-ansible.md), [AP-0048 Admin-Protected Swagger](AP-0048-admin-protected-api-documentation.md)
- **Source:** `deployment-architecture.md` Load Balancer and TLS Entry Point

## Description
Configure Nginx as the origin TLS gateway behind Cloudflare, the selected public edge, route only to healthy instances, and support secure long-lived SSE traffic. Accepted AP-0048 application/browser/domain-group proof from local or isolated staging is a prerequisite; it does not require an existing Cloudflare edge. This ticket subsequently establishes the edge and owns through-edge bootstrap, authorization and streaming regression before public release.

## Acceptance Criteria
1. Public traffic uses TLS and unhealthy instances leave rotation.
2. Forwarded headers are trusted only from configured proxy addresses.
3. SSE timeouts and heartbeats support long-lived connections.
4. PostgreSQL and Redis are not publicly exposed.
5. Authentication and upload routes receive edge rate limiting.
6. Internal transport is encrypted where the internal network is not fully trusted.
7. Accept origin traffic only through the selected Cloudflare origin path; trust forwarded client/protocol headers only from Cloudflare's current published proxy ranges or the explicitly selected authenticated tunnel. Do not expose the origin as a bypass around Cloudflare.
8. After establishing Cloudflare/origin routing, run AP-0048's accepted canonical fresh-browser launcher, seven-group JSON/YAML/download, token-isolation/cleanup and denial scenarios through the trusted HTTPS edge origin before public release. Preserve no-redirect documentation URLs and scoped credentials, reject unrelated origins/paths, retain normal API/SSE authorization, and never make documentation anonymous or widen origin access.

## Test Scenarios
1. **Given** an unhealthy instance, **when** checks fail, **then** it receives no new traffic.
2. **Given** spoofed forwarded headers, **when** sent directly, **then** they are rejected or ignored.
3. **Given** an SSE connection, **when** idle within policy, **then** it remains connected.
4. **Given** an untrusted internal network path, **when** proxy traffic reaches an application instance, **then** transport encryption is enforced.
5. **Given** a public client, **when** it connects to the service, **then** the approved public edge terminates public TLS and Nginx accepts only the configured strict-TLS origin connection before routing the request.
6. **Given** a client attempts to reach the origin directly or spoof forwarded headers, **when** traffic reaches the VPS, **then** it is denied or treated as untrusted.
7. **Given** a non-admin requests Swagger through Cloudflare, **when** the edge forwards the request, **then** the application returns the same authorization denial as local/isolated application verification.
8. **Given** accepted AP-0048 application proof and the newly established edge, **when** its trusted HTTPS origin is used by a fresh launcher context, **then** canonical HTML/assets/configuration, seven JSON/YAML groups and safe download work without documentation redirects or credential leakage; expiry/logout and ordinary API/SSE authorization retain their verified behavior before public release.

## Definition of Done
The whole ticket is implemented, every acceptance criterion and automated scenario passes, and TLS/proxy runbooks and applicable suites are complete. Accepted AP-0048 local/isolated application proof precedes edge establishment; this ticket records passing through-edge Swagger/browser, authorization, YAML/download and SSE regressions before public release, without making that edge proof a prerequisite for AP-0048 completion.
