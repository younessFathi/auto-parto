# Provider Isolation Runbook

## Scope and execution paths

| Provider | Path | Execution | Saturation/failure behavior |
|---|---|---|---|
| Firebase FCM | Background notification delivery | Firebase Admin async `ApiFuture` | Per-instance in-flight cap and batch concurrency; finite timeout and bounded retry/circuit-breaker behavior; failures are returned to the durable PostgreSQL notification retry flow. |
| Resend | OTP request | Synchronous SDK on `resend-provider` scheduler | Fixed thread pool and bounded queue; full queue rejects immediately. Timeout/cancellation interrupts the scheduled task. The caller receives a generic email-delivery error. |
| S3-compatible storage | File upload/download and metadata | AWS async client | Bounded HTTP concurrency and pending acquisition; request timeout/cancellation returns a storage failure to existing service error handling. |
| OpenBao | Host/deployment | Host Agent, outside application JVM | The agent handles renewable scoped leases and renders credential files; the app never receives its token. |

## Configuration

Tune settings through protected environment/deployment configuration. Current defaults are deliberately finite:

- Firebase: `APP_FIREBASE_PROVIDER_TIMEOUT` (10s), `APP_FIREBASE_PROVIDER_RETRY_BACKOFF` (1s), `APP_FIREBASE_PROVIDER_MAX_RETRIES` (3), `APP_FIREBASE_PROVIDER_MAX_IN_FLIGHT` (64), `APP_FIREBASE_PROVIDER_BATCH_CONCURRENCY` (32).
- Resend: `APP_EMAIL_PROVIDER_TIMEOUT` (10s), `APP_EMAIL_PROVIDER_THREADS` (4), `APP_EMAIL_PROVIDER_QUEUE_CAPACITY` (64), `APP_EMAIL_PROVIDER_SHUTDOWN_GRACE` (10s).
- S3: `APP_FILE_STORAGE_S3_MAX_CONCURRENCY` (32), `APP_FILE_STORAGE_S3_MAX_PENDING_REQUESTS` (64), `APP_FILE_STORAGE_S3_OPERATION_TIMEOUT` (30s). The AWS SDK connection-acquisition and socket timeouts are set in the storage client configuration.

Do not raise limits without checking container CPU/memory limits, provider quotas, and downstream connection capacity. Resend limits are validated at startup (1–64 threads, 1–4096 queued tasks). Firebase concurrency is validated at startup (1–512 in-flight calls, batch concurrency no greater than the in-flight cap).

## Signals and response

Inspect the private Prometheus scrape for `auto.parto.provider.calls` and group by `provider`, `operation`, and `outcome`. Outcomes are low-cardinality: `success`, `timeout`, `saturated`, `cancelled`, and `failure`. The same dimensions appear in warning logs; provider exception content, email addresses, message bodies, FCM tokens, and credentials are intentionally omitted.

- Rising `timeout` for Firebase: check provider status and outbound network latency. Notification state remains in PostgreSQL and retries follow its existing attempt/lease policy.
- Rising Firebase `saturated`: inspect delivery backlog and instance capacity before raising the in-flight cap. Rejections are safe failures that the durable notification flow can retry.
- Rising Resend `saturated`: inspect OTP request volume and provider latency; scale instances or tune the bounded pool/queue only within provider quota and memory limits. Requests fail with a generic email-delivery error.
- Rising S3 `timeout` or `failure`: inspect SeaweedFS availability, network, bucket policy, and capacity using the storage service's operational channel. Do not retry non-idempotent higher-level work without its existing idempotency protections.

At shutdown, Resend stops accepting new scheduler work, waits for the configured grace period, then interrupts remaining tasks. Firebase cancellation propagates to the SDK future. S3 client closure is managed by the Spring bean lifecycle. Do not interpret provider cancellation as proof that the remote service did not receive a request; preserve idempotent/retry handling at the business boundary.

## Verification limits

Unit tests exercise timeout, cancellation, saturation, safe error mapping, scheduler shutdown, and correlation propagation. The S3 Testcontainers test verifies async operations against a local S3-compatible server. Provider production availability, external quotas, and network-specific behavior require staging observation before go-live; those checks do not replace PostgreSQL delivery recovery.
