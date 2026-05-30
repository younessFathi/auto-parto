# US-018: Add Security and Dependency Vulnerability Scanning to CI Pipeline

**As** a security engineer  
**I want** automated security scanning (SAST, dependency check, secret detection) in the GitLab CI pipeline  
**so that** vulnerabilities in dependencies and code are detected before deployment.

## Acceptance Criteria

1. **Dependency vulnerability scanning** — `org.owasp:dependency-check-maven` plugin runs during the `test` stage
2. **Secret detection** — `gitleaks` or `trufflehog` scans the repository for committed secrets
3. **Container scanning** — If Docker image is built, `trivy` scans the image for OS-level vulnerabilities
4. **SAST** — `semgrep` or GitLab's built-in SAST scans Java source code for security antipatterns
5. Pipeline fails if any HIGH or CRITICAL vulnerability is found
6. False positives can be suppressed via an allowlist file
7. Reports are published as GitLab CI artifacts

## Technical Notes

- File: `.gitlab-ci.yml`
- For Maven: add `dependency-check-maven` plugin to `pom.xml` and call `mvn dependency-check:check`
- For secrets: `gitleaks detect --source .` in a pre-test stage
- For containers: `trivy image` after Docker build
- GitLab has built-in SAST templates: `include: { template: 'Security/SAST.gitlab-ci.yml' }`

## Risk

🟠 **HIGH** — No vulnerability detection means known CVEs in dependencies (or future ones) will go undetected until exploitation.

## Dependencies

US-019 (if container scanning is included)
