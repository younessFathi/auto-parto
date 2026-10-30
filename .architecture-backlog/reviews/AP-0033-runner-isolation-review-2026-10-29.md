# AP-0033 Runner Isolation Review

- **Ticket:** [AP-0033 GitHub Actions migration](../tickets/AP-0033-github-actions-migration.md)
- **Review Status:** Approved with operational limitation
- **Implementation Status:** Uncommitted
- **Reviewed At:** 2026-10-29

## Outcome

The concern is valid. Repository-level runner labels and workflow `if` conditions do not restrict which workflows can request a self-hosted runner. A job in a future or PR-modified workflow could target the same runner and access the private network while running untrusted code. Ephemeral lifecycle does not remove that in-job network access.

The application repository's private cutover job and manual dispatch were removed. Its ordinary CI now runs only on GitHub-hosted runners and has no database credentials. The operational gate is documented for a separate private deployment repository or external orchestrator, with the runner registered only in that deployment boundary. The deployment repository and staging gate do not exist yet, so AP-0033 remains In Progress and production migration stays unavailable.

## Findings

### High

None remaining in the application repository workflow after this correction.

### Medium

1. **Repository-level self-hosted labels were not an authorization boundary.** The previous workflow assigned private database access to a runner selected by label at `.github/workflows/ci.yml:84`. Any workflow permitted to run in the same repository could request that label. The private gate and runner selection have been removed; future operations must live in a separately controlled repository/orchestrator as documented in `docs/postgresql-development-cutover.md:90-94`.

### Low

None.

## Acceptance Criteria

| Criterion | Result | Evidence |
|---|---|---|
| Application PR workflows cannot target a private runner or access migration credentials | Pass for current repository workflow | `.github/workflows/ci.yml:19-83` uses GitHub-hosted runners only; `GithubActionsWorkflowTest.java:40-48` asserts no self-hosted runner, cutover gate, or database password configuration. |
| Private deployment jobs are outside application PR-editable workflow control | Partial | Runbook specifies a separate private deployment repository, restricted maintainers, protected workflow changes, and runner registration limited to that repository. Repository and external controls remain to be provisioned. |
| Deployment gate verifies reviewed immutable source and backup before migration | Pending | Documented requirement only; no deployment repository workflow or staging evidence exists. |
| GitLab remains until equivalent protected workflow execution is verified | Pass | `.gitlab-ci.yml` remains in the repository; AP-0033 stays In Progress. |

## Test Scenarios

| Scenario | Result | Evidence |
|---|---|---|
| Application CI workflow selects only hosted runners and excludes database credentials | Pass | `GithubActionsWorkflowTest.applicationRepositoryWorkflowNeverSchedulesJobsOnPrivateSelfHostedRunners` |
| Full unit suite | Pass | `./mvnw -B test`: 409 tests, 0 failures/errors, 1 skipped. |
| Docker/Testcontainers verification | Not completed | `clean verify` reached Failsafe but Testcontainers could not initialize Docker. Docker socket access returned `permission denied`; no Failsafe tests completed. |

## Verification

- `git diff --check` — passed.
- Ruby YAML parse of `.github/workflows/ci.yml` — passed.
- `./mvnw -B -Dtest=GithubActionsWorkflowTest test` — passed: 3 tests.
- `./mvnw -B test` — passed: 409 tests, 0 failures/errors, 1 skipped.
- `DOCKER_HOST=unix:///Users/yono/.docker/run/docker.sock ./mvnw -B clean verify` — unit suite passed; integration verification was interrupted after Testcontainers could not access the Docker socket.

## Required Before Operational Approval

1. Provision the separate private deployment repository/orchestrator and restrict workflow modification to authorized release owners.
2. Register the ephemeral runner only in that deployment boundary, configure protected environments/secrets, and complete a successful disposable/staging migration gate.
3. Retain equivalent GitHub CI and protected-gate evidence before retiring GitLab CI.

## Residual Risks

- The deployment repository/orchestrator, runner, protected environments, and secrets are not provisioned. The operational gate is intentionally unavailable.
- Docker/Testcontainers verification remains unverified in this environment due Docker socket access.
