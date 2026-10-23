# AP-0028 Isolate External Provider Clients

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md), [AP-0017](AP-0017-storage-boundary.md), [AP-0022](AP-0022-openbao-secrets.md)
- **Source:** `deployment-architecture.md` Secrets and Configuration

## Description
Keep Firebase, email, OpenBao, and object-storage calls from blocking WebFlux event-loop threads or consuming unbounded resources.

## Acceptance Criteria
1. Asynchronous clients are preferred where supported.
2. Unavoidable blocking calls use a dedicated bounded scheduler.
3. Thread count, queue size, concurrency, timeout, cancellation, and shutdown behavior are configurable and bounded.
4. Saturation and timeout outcomes are observable and mapped to safe errors or retry policy.

## Test Scenarios
1. **Given** a slow provider, **when** requests run, **then** event-loop threads remain unblocked.
2. **Given** scheduler saturation, **when** more work arrives, **then** bounded rejection occurs without unbounded memory growth.
3. **Given** shutdown with provider work active, **when** the grace period ends, **then** cancellation and cleanup follow policy.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, resource-limit configuration is documented, and all applicable suites pass.
