# AP-0022 Review Follow-up

- **Ticket:** [AP-0022 Establish OpenBao Secret Management](../tickets/AP-0022-openbao-secrets.md)
- **Original review:** [AP-0022 implementation review](AP-0022-commit-ac80599-review-2026-10-26.md)
- **Review status:** Repository-level corrections addressed; live operational acceptance remains open
- **Reviewed at:** 2026-10-26

## Outcome

The three actionable findings in the original review are addressed in the working tree. The production application unit now waits for its OpenBao Agent and checks a complete credential bundle before starting Compose; delayed rendering is covered by systemd path units and a focused test. Authentication documentation now matches the production RS256/JWKS contract. The JWKS endpoint has an unauthenticated WebFlux security/response test that checks its public key shape and absence of private key material.

The required full `verify` rerun now passes, including the two PostgreSQL SSL integration tests that failed in the original review. This establishes repository and Testcontainers evidence only. AP-0022 remains In Progress until the external OpenBao service is provisioned and actual identity, rotation, revocation, unseal, and restore drills are recorded.

## Findings addressed

### Medium 1: Agent-rendered secret bundle startup gate

`deploy/openbao/auto-parto-app.service.example` requires and orders after `openbao-agent.service`, and runs `auto-parto-check-secret-bundle` before Compose. The checker validates the rendered file type, mode/group, mandatory application credentials, RS256 material, enabled provider credentials, and the separate audit bundle when audit is enabled. The audit drop-in adds the audit Agent dependency. Both path units include `PathExists` so an Agent that renders after delayed login can trigger the fixed refresh service. `OpenBaoSecretReadinessScriptTest` covers missing/incomplete bundles, a complete bundle, audit gating, unit dependencies, and late render/recovery.

### Medium 2: Canonical JWT signing guidance

`docs/canonical-authentication.md` now documents local/test HS256 compatibility separately from production RS256 signing, the public JWKS endpoint, and key overlap/removal. `SecretsManagementConfigTest` asserts the production contract in the operator-facing documentation.

### Low 1: JWKS HTTP/security contract

`JwtTokenAuthenticationFilter` explicitly permits only the exact `/.well-known/jwks.json` path. `JwksControllerSecurityTest` makes an unauthenticated HTTP request and asserts JSON/JWK metadata and no private fields. `JwtTokenGeneratorTest` also asserts generated overlap JWKS entries omit private key fields.

## Verification

- `./mvnw -B -Dtest=OpenBaoSecretReadinessScriptTest test` — passed, 4 tests.
- `./mvnw -B -Dtest=JwksControllerSecurityTest test` — passed, 1 test (run with host JVM attachment enabled because the sandbox blocks Mockito's dynamic agent attach).
- `./mvnw -B test` — passed on the final tree, 389 tests, 1 skipped.
- `DOCKER_HOST=unix:///Users/yono/.docker/run/docker.sock ./mvnw -B verify` — passed, 143 integration tests, 1 skipped; includes `PostgresqlExistingVolumeRecoveryIT`, `PostgresqlDevelopmentFixturesIT`, and `DeploymentComposeIT`; coverage checks passed.
- `bash -n deploy/openbao/auto-parto-check-secret-bundle` — passed.
- `git diff --check` — passed.

## Residual risks and release blockers

- The systemd files remain deployment templates and have not been installed or exercised on a staging host.
- No external OpenBao service, workload binding, lease renewal/revocation, credential rotation/old-credential rejection, unseal, backup, or restore drill has been executed. AP-0022 and F07 must remain In Progress; F09 remains blocked on operational evidence.
