# US-020: Add HEALTHCHECK, Resource Limits, and Logging Config to Container Setup

**As** a platform engineer  
**I want** the Dockerfile and docker-compose.yml to include HEALTHCHECK instructions, container resource limits, and log rotation  
**so that** the container orchestrator can detect application health and containers do not consume unbounded host resources.

## Acceptance Criteria (Dockerfile)

1. `HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 CMD wget -qO- http://localhost:8080/actuator/health || exit 1` is added
2. JVM memory flags are set: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`

## Acceptance Criteria (docker-compose.yml)

3. All services have `mem_limit` and `cpus` limits:
   - `springboot-app`: `mem_limit: 1g`, `cpus: "1.0"`
   - `mongo`: `mem_limit: 2g`, `cpus: "2.0"`
   - `redis`: `mem_limit: 512m`, `cpus: "0.5"`
4. All services have `restart: unless-stopped`
5. Logging driver is configured with rotation: `max-size: "10m"`, `max-file: "3"`
6. Healthcheck is added to the `springboot-app` service in docker-compose.yml
7. `depends_on` for the app service uses `condition: service_healthy`

## Technical Notes

- Files: `Dockerfile`, `docker-compose.yml`
- The HEALTHCHECK assumes `wget` is available in the Alpine JRE image (install via `apk add --no-cache wget`)
- Resource limits should be tuned based on actual load testing results

## Risk

🟡 **MEDIUM** — Without healthchecks, orchestrators treat crashed/hanging containers as healthy. Without limits, a single container can exhaust host resources.

## Dependencies

US-015 (Actuator health endpoint)
