# US-023: Add Security Integration Tests

**As a** security engineer  
**I want** automated tests that verify authentication and authorization enforcement  
**so that** security regressions are detected immediately and the security configuration is verified.

## Acceptance Criteria

1. Tests verify that unauthenticated requests to protected endpoints return 401:
   - All `/seller/**`, `/customers/**`, `/pieces/**`, `/api/v1/notifications/**`, `/notifications/**`,
     `/images/**`, `/api/clerk/**`, `/google/**` endpoints
2. Tests verify that missing/invalid/expired JWT tokens return 401
3. Tests verify `@PreAuthorize` role checks:
   - `POST /api/v1/notifications/fcm/notify/search` requires BUYER role
   - `POST /api/v1/notifications/fcm/notify/response` requires SELLER role
   - `POST /api/v1/notifications/fcm/urgent` requires ADMIN role
   - `GET /api/v1/notifications/fcm/health` requires ADMIN role
4. Tests verify public endpoints are accessible without authentication:
   - All `/auth/**` endpoints
   - Swagger endpoints
5. Tests verify that JWT tokens with wrong roles are rejected for role-protected endpoints
6. The `SecurityHelper` utility is tested for each `validate*` method

## Technical Notes

- Use `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` with Testcontainers for full security context
- Generate test JWT tokens programmatically using `JwtTokenGenerator` for different roles
- Use `@WithMockUser` or manually set `SecurityContext` for role tests

## Risk

🟠 **HIGH** — No security tests means auth bypasses or misconfigurations silently ship to production.

## Dependencies

US-003 (JWT filter fix), US-022 (controller test infrastructure)
