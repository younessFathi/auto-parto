# AP-0022 Establish OpenBao Secret Management

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md)
- **Source:** `deployment-architecture.md` Secrets and Configuration

## Description
Deliver environment-isolated application credentials through protected OpenBao policies and controlled rotation.

## Acceptance Criteria
1. Required database, Redis, JWT, OAuth, Firebase, email, and storage secrets are externalized.
2. Workloads authenticate with short-lived machine identity and least-privilege policies.
3. Production fails safely when required secrets are missing and never falls back to development values.
4. OpenBao is private, TLS-protected, backed up, and outside the database failure boundary.
5. Rotation and recovery procedures are documented and tested.
6. Local uses safe optional defaults, test uses isolated fake/container providers, and production requires validated external secrets.
7. JWT signing prefers asymmetric rotating keys with published verification keys and a tested overlap window.

## Test Scenarios
1. **Given** a production workload, **when** it requests another environment's secret, **then** access is denied.
2. **Given** a missing required secret, **when** production starts, **then** startup fails safely.
3. **Given** a rotated credential, **when** rollout completes, **then** the old credential is revoked safely.
4. **Given** JWT key rotation, **when** the overlap window is active, **then** new and allowed prior tokens verify through published keys.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, secret rotation and recovery runbooks are complete, and all applicable suites pass.
