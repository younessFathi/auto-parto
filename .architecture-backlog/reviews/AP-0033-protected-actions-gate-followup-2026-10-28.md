# AP-0033 Protected Actions Gate Follow-up Review

- **Ticket:** [AP-0033 GitHub Actions migration](../tickets/AP-0033-github-actions-migration.md)
- **Reviewed finding:** [AP-0033 protected actions gate review](AP-0033-protected-actions-gate-review-2026-10-28.md)
- **Commits:** `d12f7f7`, `feb60aa` plus this follow-up
- **Review Status:** Changes Required
- **Implementation Status:** Committed follow-up on `feat/ap-0033-protected-actions-gate`
- **Reviewed At:** 2026-10-28

## Outcome

The code corrections address both high findings: a manual cutover cannot start unless unit and integration suites pass, and database credentials are only injected into a job assigned to the dedicated private self-hosted runner label. The current repository has no such runner and no `postgresql-cutover` GitHub Environment. The gate is therefore not operationally verified, and the full ticket remains In Progress.

## Findings

### High

1. **Resolved in workflow:** `postgresql-migration-gate` now depends on both `test` and `integration-test` at `.github/workflows/ci.yml:81-85`. The focused workflow test asserts both dependencies at `src/test/java/auto/parto/ci/GithubActionsWorkflowTest.java:42-58`.
2. **Code path corrected; deployment control pending:** the cutover job now selects the `ap-0033-private-cutover` runner group with self-hosted Linux labels and disables checkout credential persistence at `.github/workflows/ci.yml:84-93`. It no longer sends database credentials to a GitHub-hosted runner. The group must contain only restricted one-job ephemeral runners inside the private database network. No runner or protected environment is configured, so no private database operation was attempted.

### Medium

None.

### Low

None.

## Acceptance Criteria

| Criterion | Result | Evidence |
|---|---|---|
| Unit and integration failures prevent cutover mutation | Pass | `ci.yml:81-85`; workflow regression assertion at `GithubActionsWorkflowTest.java:55-58` |
| PostgreSQL credentials stay off GitHub-hosted runners | Pass for checked-in workflow | `ci.yml:84-93`; dedicated self-hosted runner group and non-persistent checkout credentials |
| Private ephemeral runner, protected environment, and environment secrets are configured | Partial | Runbook documents the required controls; repository environment query previously returned zero environments and no matching runner is provisioned |
| Protected manual gate completes against staging | Not verified | Requires the private runner, protected environment, and disposable/staging database |
| GitLab remains active until equivalent protected gate is verified | Pass | `.gitlab-ci.yml` remains unchanged and active |

## Test Scenarios

| Scenario | Result | Evidence |
|---|---|---|
| Unit test failure blocks cutover job | Pass by dependency contract | `needs: [test, integration-test]` and automated assertion |
| Hosted runner cannot receive database credentials | Pass by runner selection | Cutover job requires the dedicated self-hosted label; no hosted-runner fallback |
| Missing private runner prevents cutover from reaching PostgreSQL | Pending operational verification | No runner is configured in the required group; job cannot be scheduled |
| Manual environment approval and secret delivery | Pending | GitHub Environment is not configured |

## Verification

- `git diff --check` — passed.
- Ruby YAML parse of `.github/workflows/ci.yml` — passed.
- `./mvnw -B -Dtest=GithubActionsWorkflowTest test` — passed: 3 tests.
- `./mvnw -B -Dtest=GithubActionsWorkflowTest,PostgresqlMigrationResourceTest test` — passed: 18 tests.
- `./mvnw -B test` — passed: 409 tests, 0 failures/errors, 1 skipped.
- `./mvnw -B verify` — passed: 409 unit tests and 145 integration tests, 1 skipped in each suite; JaCoCo passed.

## Required Before Approval

1. Provision a restricted runner group for this repository containing only one-job ephemeral runners on the private database network, with least-privilege network egress and no use by pull-request workflows.
2. Configure the `postgresql-cutover` GitHub Environment for `main`, require independent review, and store only the necessary environment secrets there.
3. Run the manual gate against a disposable/staging PostgreSQL target and retain successful end-to-end evidence. Keep GitLab CI active until this passes.

## Residual Risks

- The self-hosted runner's ephemeral lifecycle, private network placement, runner-group access rules, and environment review settings are external GitHub/hosting configuration and were not available for verification.
- The cutover workflow validates a backup attestation; it does not take or restore the backup.
