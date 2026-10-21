# AP-0033: GitHub Actions migration

## Status

In progress.

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
5. GitHub Actions is validated before GitLab CI is retired.

## Implementation Tracking

- Added `.github/workflows/ci.yml` with the preserved Maven and PostgreSQL gate stages.
- Added `GithubActionsWorkflowTest` for workflow structure, report retention, and gate configuration.
