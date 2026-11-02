# AP-0033: GitHub Actions migration

## Status

In Progress.

## Goal

Move repository CI from GitLab CI to GitHub Actions while preserving build, unit-test, Testcontainers verification, and package behavior. Keep private database access in a separately controlled deployment repository or external orchestrator.

## Scope

- Add a GitHub Actions workflow for pull requests, main, and supported feature branches. Do not assign private runners or database credentials to workflows in this application repository.
- Run Java 21 Maven compile, unit tests, integration/container verification, and packaging.
- Publish only short-lived Surefire and Failsafe reports; do not publish application packages as CI artifacts.
- Run cutover migration, reconciliation, and preflight only from a separately controlled deployment repository or external orchestrator.
- Document the migration and validate the workflow configuration with automated tests.

## Acceptance Criteria

1. Pull requests and pushes to supported branches run compile, unit, integration/container, and package stages.
2. Test reports are retained for one day and package output is not uploaded as a CI artifact.
3. No application pull-request workflow can access a private database runner or credentials. A separately controlled deployment repository/orchestrator validates an immutable reviewed application commit and backup attestation before running Flyway, fixtures, reconciliation, and preflight checks.
4. GitHub Actions workflow configuration has focused automated coverage.
5. GitHub Actions is validated and available as the preferred CI path, while the operational GitLab pipeline remains active until deliberate retirement is completed.

## Implementation Tracking

- Added `.github/workflows/ci.yml` with Maven compile, unit, integration/container, and package stages.
- Added `GithubActionsWorkflowTest` for workflow structure, report retention, and absence of private self-hosted runners/database credentials in application-repository CI.
- GitHub Actions workflow coverage is in place. The operational `.gitlab-ci.yml` remains a supported pipeline until retirement is deliberately implemented and verified.
- The application repository no longer contains a manual cutover workflow or private runner selection. Runner labels and workflow conditions are not an authorization boundary because any permitted workflow can request a repository-level runner. The operational gate must live in a separately controlled private deployment repository or external orchestrator, as specified in `docs/postgresql-development-cutover.md`. AP-0033 remains In Progress until that external control, protected environments, scoped credentials, and a successful staging gate run are verified.
- Historical remote verification: GitHub Actions run 59 for commit `d12f7f7` succeeded for build, unit tests, Docker/Testcontainers integration verification, and packaging. The then-present manual cutover job was skipped on the push event. A read-only GitHub API check found no configured repository environments. This application-repository gate has since been removed for runner isolation.
- Historical review follow-up selected a repository-level runner. This was superseded after identifying that repository runner labels are available to any workflow in the repository; no such runner should be registered here.
- Runner isolation correction: the application-repository gate and runner selection have been removed. The deployment repository/orchestrator is not yet provisioned, so the protected operational gate is intentionally unavailable and GitLab remains active.
- Related infrastructure boundary: AP-0046 state-changing migrations/deployments must run through the separately controlled deployment repository/orchestrator, with private PostgreSQL reachability and no access from application PR workflows. Keep GitLab active until protected end-to-end staging verification succeeds and retirement is approved.
