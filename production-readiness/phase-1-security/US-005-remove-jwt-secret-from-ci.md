# US-005: Remove Hardcoded JWT Secret from CI Configuration

**As a** security engineer  
**I want** the `JWT_SECRET` variable to be removed from `.gitlab-ci.yml` and set via CI/CD secure variables instead  
**so that** the JWT signing key is not visible in plaintext to anyone with repository access.

## Acceptance Criteria

1. `JWT_SECRET` is removed from the `variables` section of `.gitlab-ci.yml`
2. The variable is configured in GitLab CI/CD Settings → CI/CD Variables as a **Masked** variable
3. The existing secret value is rotated (since it has been exposed)
4. All pipeline stages that need `JWT_SECRET` can access it from the environment

## Technical Notes

- File: `.gitlab-ci.yml` line 10
- Current value: `bmgTK4gXGmWEbq3jc3zluPcMtomqqpMSO1VY7R0QqYQ=`
- This secret is used during tests — ensure the new CI variable is available to the `test` and `integration-test` jobs

## Risk

🔴 **CRITICAL** — JWT signing key exposed in repository. Anyone with repo access can forge tokens.

## Dependencies

US-001 (secret revocation)
