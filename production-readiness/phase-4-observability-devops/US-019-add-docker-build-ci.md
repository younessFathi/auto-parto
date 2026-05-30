# US-019: Add Docker Image Build and Push Stage to CI Pipeline

**As** a DevOps engineer  
**I want** the CI pipeline to build and push a Docker image to a container registry  
**so that** the application can be deployed to production environments from a versioned, reproducible image.

## Acceptance Criteria

1. A new `docker-build` stage is added after `package` in `.gitlab-ci.yml`
2. The Docker image is built using the `Dockerfile` and tagged with:
   - Git commit SHA (`registry.example.com/auto-parto:abc1234`)
   - Git branch or tag (`registry.example.com/auto-parto:main`)
   - Semantic version if a tag is pushed (`registry.example.com/auto-parto:v1.2.3`)
3. The image is pushed to a container registry (GitLab Container Registry, Docker Hub, or ECR)
4. The existing `.dockerignore` (if any) is verified to exclude `src/`, `.env`, and other build-time-only files
5. CI variables for registry credentials are securely configured (not hardcoded)
6. The image is scanned with Trivy as part of the build stage (see US-018)

## Technical Notes

- File: `.gitlab-ci.yml`
- GitLab CI has built-in `docker build` and `docker push` support with `docker:dind`
- Use `$CI_REGISTRY_IMAGE` for GitLab Container Registry
- Example tag scheme: `$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA`

## Risk

🟡 **MEDIUM** — Without automated image builds, deployments use manually built JARs with no provenance tracking.

## Dependencies

US-018 (container scanning integrated into build stage)
