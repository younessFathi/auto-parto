# Auto-Parto Agent Guide

This file is the contribution guide for human developers and AI coding agents.
Read it before inspecting or changing the codebase.

## Project Summary

Auto-Parto is a Java 21 Spring Boot WebFlux backend for a car-parts marketplace.
It uses:

- MongoDB for buyers, sellers, pieces, offers, wallets, addresses, and files.
- Redis for caching, rate limiting, device tokens, pending notifications, and retry queues.
- JWT/Google authentication, OTP verification, and role-based authorization.
- Firebase Cloud Messaging and Server-Sent Events for notifications.
- Maven, Docker, Docker Compose, and GitLab CI.

## Before Making Changes

1. Read this file completely.
2. Inspect the relevant controller, service, repository, DTO/model, mapper, and test files before editing.
3. Check the current working tree. Do not overwrite or revert unrelated user changes.
4. Check `.architecture-backlog/` for related tickets and follow-up work.
5. Keep changes focused. Do not perform broad refactors unless explicitly requested.

## Project Layout

- `src/main/java/auto/parto/api/`: HTTP and SSE controllers.
- `src/main/java/auto/parto/service/`: business services.
- `src/main/java/auto/parto/repository/`: repository interfaces and MongoDB implementations.
- `src/main/java/auto/parto/model/`: MongoDB domain models.
- `src/main/java/auto/parto/dto/`: request and response objects.
- `src/main/java/auto/parto/mapper/`: MapStruct and database mappings.
- `src/main/java/auto/parto/config/`: security, Redis, Firebase, executor, and application configuration.
- `src/main/java/auto/parto/service/notification/`: FCM, SSE, Redis notification storage, and retry logic.
- `src/test/`: unit, controller, security, repository, and integration tests.
- `production-readiness/`: legacy reference material only; do not use it for active planning, ticket tracking, or updates.
- `.agent-trace/`: local, ignored prompt summaries and change records for human/AI traceability.

## Legacy Reference Material

Treat `production-readiness/` as archived reference material. Do not create, update, close, or derive active work from its stories. Use `.architecture-backlog/` for all active tickets, follow-ups, and implementation tracking. Consult `production-readiness/` only when historical context is specifically useful.

## Java and Spring Style

- Use Java 21 features only when they improve clarity.
- Prefer constructor injection. Use `final` dependencies where practical.
- Keep controllers thin. Put business rules in services and persistence logic in repositories.
- Use DTOs at API boundaries. Do not expose MongoDB models directly from controllers.
- Use MapStruct for existing entity/DTO mappings instead of duplicating mapping code.
- Follow existing package and naming conventions.
- Prefer small methods with one responsibility.
- Store money as integer minor units, such as cents: `1000` means `$10.00`.
- Prefer `long` for monetary values instead of `Integer` to reduce overflow risk.
- Store the currency with the amount and apply the currency's correct minor-unit scale.
- Use overflow-safe arithmetic such as `Math.addExact` where appropriate.
- Never use `double` or `float` for balances or prices, and never convert money through them.
- Validate request input with Jakarta Validation and handle invalid input consistently.
- Use domain-specific exceptions where they improve the API error response.
- Do not add comments that merely restate the code. Add comments only for non-obvious decisions.
- Avoid adding interfaces or abstractions without a real second implementation or a clear testing/design need.

## Reactive Programming Rules

- Preserve non-blocking behavior in WebFlux request paths.
- Return `Mono` or `Flux` from reactive services instead of blocking.
- Do not call `.block()` in application code.
- Do not call `.subscribe()` inside request handling or service methods just to make a chain run. Return the chain to the caller.
- If work is intentionally background/asynchronous, document its lifecycle, retry behavior, and error handling.
- Be explicit about empty results with `switchIfEmpty` where the API needs a not-found response.
- Do not wrap reactive code in unnecessary `Mono.fromCallable` calls.
- Bound buffers, queues, concurrency, and retries. Never introduce unbounded user-controlled memory growth.

## Security Rules

- Never commit secrets, tokens, private keys, credential JSON, or real personal data.
- Never include secret values in documentation, examples, tests, logs, or error responses.
- Use environment variables or an approved secret manager for credentials.
- Treat `.env` and Firebase credential files as local-only files.
- Never trust a user ID, seller ID, buyer ID, or token owner ID supplied by a request. Derive identity from the authenticated principal and verify ownership.
- Every sensitive endpoint must enforce both authentication and authorization.
- Review role checks and resource ownership separately. A valid role does not automatically grant access to every resource.
- Do not log OTPs, JWTs, notification bodies, private keys, passwords, or complete personal data.
- Keep authentication behavior consistent. Do not introduce a second JWT/authentication mechanism without documenting how it is enabled and tested.
- Preserve rate limits for authentication, OTP, token registration, and notification endpoints.
- Validate uploaded files by content, size, extension, and safe destination path. Do not rely on a filename extension alone.

## Data and Distributed-System Rules

- Use atomic MongoDB updates for balances, counters, and state transitions.
- Add idempotency for operations that can be retried or repeated, especially wallet and notification operations.
- For money movement, preserve an auditable transaction history; do not rely only on the current balance.
- Design Redis multi-step operations for concurrency. Prefer atomic Redis commands, transactions, or Lua scripts.
- Do not load an entire Redis collection or sorted set into application memory without a strict bound.
- Ensure queued work can recover after an application restart.
- Consider duplicate delivery, retry exhaustion, dead-letter handling, and multi-instance deployment for notifications.
- Add or update MongoDB indexes when adding new query patterns.
- Do not enable automatic schema/index behavior in production without understanding its operational impact.

## API and Error Handling

- Preserve established URL and response contracts unless the change explicitly requires a breaking change.
- Use correct HTTP status codes: validation errors as 400, missing resources as 404, authentication failures as 401, authorization failures as 403, and accepted asynchronous work as 202.
- Do not return internal exception messages to clients unless they are safe and intentional.
- Keep error responses consistent with existing exception handlers.
- Document new public endpoints with the existing OpenAPI annotations where appropriate.

## Testing Requirements

For every behavior change:

- Add or update a focused unit test.
- Add a controller/security test for authorization or HTTP behavior changes.
- Add an integration test for MongoDB, Redis, concurrency, retry, or startup-recovery behavior when relevant.
- Test success, validation failure, missing data, unauthorized access, and dependency failure paths.
- Test retry and duplicate-request behavior for asynchronous operations.
- Do not weaken or delete a test merely to make the build pass.

Run the following before submitting changes:

```bash
./mvnw -B test
```

Integration tests use Testcontainers and require Docker:

```bash
./mvnw -B verify
```

If Docker is unavailable, report that integration tests could not run. Do not claim the full suite passed.

## Configuration and Deployment

- Keep secrets out of `application*.yml`; use environment variables or secret management.
- Keep local, test, and production behavior separated by Spring profiles.
- Do not use development passwords or JWT keys in production defaults.
- Do not expose MongoDB or Redis ports in production deployment configuration unless explicitly required.
- Preserve non-root container execution and resource limits.
- Health checks must work without requiring an application JWT, or the security exception must be tested explicitly.
- New configuration values need safe defaults for tests and clear production validation.
- Changes to CI must keep unit tests, integration tests, packaging, and security checks understandable and reproducible.

## Documentation Rules

- Update `README.md` when setup, architecture, or runtime behavior changes.
- Update the relevant `.architecture-backlog` ticket when a documented issue is fixed or follow-up work is identified.
- Do not put real credentials in issue descriptions, stories, fixtures, or examples.
- Keep documentation readable by both humans and coding agents.

## Prompt Traceability

Every human or AI prompt or contribution session must record a concise trace in `.agent-trace/`, including read-only reviews, explanations, and implementation requests.

- Read `.agent-trace/README.md` before creating or updating a trace.
- Create one new Markdown file per completed prompt or contribution session.
- Record the prompt/request summary, work performed, final result/output summary, files changed, tests run, and unresolved risks.
- Never overwrite, delete, or modify an existing trace file when recording a new prompt. If the proposed filename already exists, use a unique suffix such as `-2`, `-3`, or a timestamp.
- Treat existing trace files as immutable historical records unless the user explicitly requests a correction to that specific trace.
- Do not copy secrets, tokens, passwords, private keys, personal data, or complete sensitive payloads into a trace.
- Use a safe short filename such as `YYYY-MM-DD-<short-topic>.md`.
- Trace files are intentionally ignored by Git and are for local collaboration/debugging only.
- A trace does not replace Git history, issue tracking, code review, or deployment records.

## Agent Output and Change Discipline

- Before editing, state the intended scope when the task is non-trivial.
- Prefer the smallest correct change.
- Do not modify unrelated files.
- Do not create commits unless the user explicitly asks for one.
- Do not run destructive Git commands such as `git reset --hard` or `git checkout --`.
- Report files changed, tests run, test limitations, and any unresolved risks.
- If a requirement conflicts with security, data integrity, or an existing user change, stop and ask for clarification.

## Contribution Checklist

Before editing:

- Read this file and `.agent-trace/README.md`.
- Check `git status` and preserve unrelated user changes.
- Inspect the relevant implementation, tests, configuration, and architecture-backlog ticket.
- Identify authorization, ownership, money, persistence, retry, and resource-lifecycle effects.
- Check whether PostgreSQL indexes, constraints, migrations, or Redis bounds are required.
- Check retry, duplicate-request, and restart behavior for asynchronous work.
- For deployment changes, check database backups, secret rotation, health checks, resource limits, TLS, and rollback behavior.

Before reporting completion:

- Add or update focused tests for behavior changes.
- Run `./mvnw -B test`.
- Run `./mvnw -B verify` when Docker is available.
- Confirm no secrets or sensitive data were added.
- Update relevant documentation and architecture-backlog tracking.
- Create a sanitized trace in `.agent-trace/`.
- Report changed files, verification results, limitations, and unresolved risks.

Deployment review must also confirm:

- PostgreSQL is private, backed up, and recoverable.
- Redis data is classified as disposable, recoverable queue data, or PostgreSQL-owned business data.
- Object storage uses SeaweedFS through the storage abstraction and has tested recovery.
- Public IDs and internal database IDs are not confused or exposed incorrectly.
- Secrets are supplied through OpenBao or an approved protected secret system.
- The load balancer has TLS, trusted proxy handling, health checks, and SSE timeouts.
- CloudEvents are used only when an external or cross-service event contract is needed, not as a log format.

## Definition of Done

A change is complete when:

- The implementation follows the relevant rules above.
- Tests cover the changed behavior and pass in the available environment.
- No secrets or sensitive data were added.
- Authorization and ownership were considered.
- Reactive and resource-lifecycle behavior is safe.
- Documentation or architecture-backlog tracking is updated when needed.
- The final report clearly states what changed and what could not be verified.
