# AP-0028 Isolate External Provider Clients

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md), [AP-0017](AP-0017-storage-boundary.md), [AP-0022](AP-0022-openbao-secrets.md)
- **Source:** `deployment-architecture.md` Secrets and Configuration

## Description
Keep Firebase, email, OpenBao, and object-storage calls from blocking WebFlux event-loop threads or consuming unbounded resources.

## Provider Operation Inventory

| Provider operation | Client/execution | Path | Bounds and failure behavior |
|---|---|---|---|
| Firebase Admin send | `FirebaseMessaging.sendAsync(ApiFuture)` adapted to Reactor; no blocking scheduler | Background notification delivery | In-flight semaphore and batch concurrency, finite timeout, future cancellation, bounded retry policy, circuit breaker; safe result maps to PostgreSQL delivery retry |
| Resend email send | Synchronous Resend Java SDK on `resend-provider` bounded scheduler | OTP request path | Fixed threads, bounded queue, rejection, timeout/cancellation, graceful shutdown; safe `EmailSendException` |
| S3/SeaweedFS object operations | AWS `S3AsyncClient` | Upload/download and storage service paths | Finite HTTP concurrency and pending acquisition bound, connection/acquisition/API timeouts, cancellation, graceful client close |
| OpenBao | No application HTTP client; host Agent renders scoped credential files | Host/deployment lifecycle | Outside JVM provider execution; renewal/revocation and policy isolation tracked by AP-0022/AP-0044 |
| Firebase health | Local initialized-app presence check only | Readiness indicator | No network call |

## Acceptance Criteria
1. Asynchronous clients are preferred where supported; the inventory identifies every external provider operation and whether it is blocking and request or background path.
2. Unavoidable blocking calls use a dedicated bounded scheduler.
3. Thread count, queue size, concurrency, timeout, cancellation, and shutdown behavior are configurable and bounded.
4. Saturation and timeout outcomes are observable through low-cardinality metrics and redacted logs, and map to safe errors or existing retry policy.
5. Reactor context/correlation propagation survives provider scheduler and asynchronous callback boundaries; provider failures never run blocking work on Netty event loops.

## Test Scenarios
1. **Given** slow Resend, **when** an OTP request runs, **then** it times out on the dedicated scheduler without blocking event loops and returns a safe error.
2. **Given** scheduler saturation or Firebase in-flight saturation, **when** more work arrives, **then** bounded rejection occurs without unbounded memory growth and emits a saturation metric.
3. **Given** timeout or downstream cancellation, **when** provider work is active, **then** cancellation is propagated where supported and cleanup follows policy.
4. **Given** shutdown with blocking provider work active, **when** grace expires, **then** the executor interrupts remaining work and closes safely.
5. **Given** a provider callback crosses execution boundaries, **when** a request is correlated, **then** its Reactor/MDC correlation remains available and logs contain no provider response, token, or payload.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, resource-limit configuration is documented, and all applicable suites pass. The PostgreSQL notification intent/claim/attempt/retry state remains authoritative; provider isolation does not create a second delivery authority.

## Verification Evidence
- Firebase async send, timeout/cancellation, in-flight saturation, and safe retry outcome: `ResilientFcmServiceTest` and `ResilientFcmServiceTimeoutTest`.
- Resend blocking isolation, timeout/interruption, bounded queue rejection, safe exception mapping, and request ID propagation: `EmailServiceIsolationTest` and `BoundedProviderSchedulerTest`.
- Executor bounded rejection and graceful shutdown/forced interruption: `BoundedProviderSchedulerTest`.
- Async S3 put/head/list/read and outcome metrics against S3Mock: `S3CompatibleFileStorageIT`; bound validation: `S3CompatibleFileStorageTest`.
- Focused command passed: `./mvnw -B -Dtest=EmailServiceIsolationTest,BoundedProviderSchedulerTest,ResilientFcmServiceTest,ResilientFcmServiceTimeoutTest,S3CompatibleFileStorageTest,S3CompatibleFileStorageIT,FirebaseConfigTest test`.
- `./mvnw -B test`: passed, 405 tests (1 skipped).
- `./mvnw -B verify`: passed, 405 unit tests plus 145 integration tests (1 skipped in each phase).
