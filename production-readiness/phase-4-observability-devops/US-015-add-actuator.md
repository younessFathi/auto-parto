# US-015: Add Spring Boot Actuator with Health, Metrics, and Prometheus

**As** an operations engineer  
**I want** Spring Boot Actuator with health endpoints, metrics, and Prometheus scraping  
**so that** Kubernetes liveness/readiness probes work, and I can monitor application health and performance in production.

## Acceptance Criteria

1. `spring-boot-starter-actuator` and `micrometer-registry-prometheus` are added to `pom.xml`
2. `/actuator/health` is publicly accessible (unauthenticated) for k8s probes:
   - MongoDB health indicator is active
   - Redis health indicator is active
   - Firebase connectivity is included (custom health indicator)
3. `/actuator/prometheus` is accessible for Prometheus scraping (potentially restricted to internal network)
4. The custom FCM health endpoint remains as an admin-only detailed check
5. `management.endpoints.web.exposure.include` is explicitly configured (not wildcard `*`)
6. `/actuator/info` exposes build info (git commit, build time, version)

## Technical Notes

- Dependencies: `spring-boot-starter-actuator`, `micrometer-registry-prometheus`
- Config: `management.endpoint.health.show-details: when-authorized`, `management.endpoints.web.exposure.include: health,info,prometheus`
- The existing `FcmNotificationController.healthCheck()` can remain as an admin deeper-dive
- Consider adding a custom `FirebaseHealthIndicator` implementing `ReactiveHealthIndicator`

## Risk

🟠 **HIGH** — Without health endpoints, container orchestrators cannot detect application failure. No metrics means blind deployment.

## Dependencies

None.
