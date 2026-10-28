# AP-0033: GitHub Actions migration

## Status

In Progress.

## Goal

Move repository CI from GitLab CI to GitHub Actions while preserving build, unit-test, Testcontainers verification, package, and optional PostgreSQL cutover-gate behavior.

## Scope

- Add a GitHub Actions workflow for pull requests, main, feature branches, and manual cutover-gate runs.
- Run Java 21 Maven compile, unit tests, integration/container verification, and packaging.
- Publish only short-lived Surefire and Failsafe reports; do not publish application packages as CI artifacts.
- Keep cutover migration, reconciliation, and preflight jobs manual and protected by GitHub Actions secrets.
- Document the migration and validate the workflow configuration with automated tests.

## Acceptance Criteria

1. Pull requests and pushes to supported branches run compile, unit, integration/container, and package stages.
2. Test reports are retained for one day and package output is not uploaded as a CI artifact.
3. Manual cutover execution validates required secrets before running Flyway, fixtures, reconciliation, and preflight checks.
4. GitHub Actions workflow configuration has focused automated coverage.
5. GitHub Actions is validated and available as the preferred CI path, while the operational GitLab pipeline remains active until deliberate retirement is completed.

## Implementation Tracking

- Added `.github/workflows/ci.yml` with the preserved Maven and PostgreSQL gate stages.
- Added `GithubActionsWorkflowTest` for workflow structure, report retention, and gate configuration.
- GitHub Actions workflow coverage is in place. The operational `.gitlab-ci.yml` remains a supported pipeline until retirement is deliberately implemented and verified.
- The manual cutover is restricted to `main`, uses the `postgresql-cutover` protected GitHub Environment, validates backup/cutover attestations before Flyway, and performs migration, fixture, reconciliation, and final preflight steps sequentially. Repository environment reviewer/branch protection and secret configuration, an end-to-end GitHub Actions run, and GitLab retirement remain operational acceptance evidence; the ticket stays In Progress until those are verified.
- Remote verification: GitHub Actions run 59 for commit `d12f7f7` succeeded for build, unit tests, Docker/Testcontainers integration verification, and packaging. The manual cutover job was skipped on the push event. A read-only GitHub API check returned zero configured repository environments, so `postgresql-cutover` protection and environment secrets are not configured. GitLab remains active until the protected manual gate is verified.
- Review follow-up: the cutover job now depends on both unit and integration checks and targets a repository-level self-hosted runner with the `ap-0033-private-cutover` label, which is valid for this user-owned repository and cannot resolve to a GitHub-hosted runner. Configure only an isolated one-job ephemeral runner inside the private database network, and keep the gate unavailable until the runner and protected environment exist. These external controls and a protected staging gate run remain unverified.
