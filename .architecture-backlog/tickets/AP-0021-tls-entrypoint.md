# AP-0021 Configure TLS Entry Point

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md), [AP-0028](AP-0028-provider-isolation.md)
- **Source:** `deployment-architecture.md` Load Balancer and TLS Entry Point

## Description
Configure Nginx as the origin TLS gateway behind the approved public edge, route only to healthy instances, and support secure long-lived SSE traffic.

## Acceptance Criteria
1. Public traffic uses TLS and unhealthy instances leave rotation.
2. Forwarded headers are trusted only from configured proxy addresses.
3. SSE timeouts and heartbeats support long-lived connections.
4. PostgreSQL and Redis are not publicly exposed.
5. Authentication and upload routes receive edge rate limiting.
6. Internal transport is encrypted where the internal network is not fully trusted.

## Test Scenarios
1. **Given** an unhealthy instance, **when** checks fail, **then** it receives no new traffic.
2. **Given** spoofed forwarded headers, **when** sent directly, **then** they are rejected or ignored.
3. **Given** an SSE connection, **when** idle within policy, **then** it remains connected.
4. **Given** an untrusted internal network path, **when** proxy traffic reaches an application instance, **then** transport encryption is enforced.
5. **Given** a public client, **when** it connects to the service, **then** the approved public edge terminates public TLS and Nginx accepts only the configured strict-TLS origin connection before routing the request.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, TLS and proxy runbooks are complete, and all applicable suites pass.
