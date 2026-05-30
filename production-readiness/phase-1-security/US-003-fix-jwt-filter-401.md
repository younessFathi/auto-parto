# US-003: Fix JWT Authentication Filter to Return 401 on Invalid Tokens

**As a** security engineer  
**I want** `JwtTokenAuthenticationFilter` to return HTTP 401 Unauthorized when the JWT token is missing, expired, or invalid  
**so that** unauthenticated requests are immediately rejected rather than silently passed through to the security chain.

## Acceptance Criteria

1. When no `Authorization` header is present, the filter returns 401 and completes the response
2. When the token fails validation (expired, malformed, wrong signature), the filter returns 401
3. When a valid token is present, the filter proceeds as before (setting `SecurityContext`)
4. Public endpoints (`/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/webjars/**`) are correctly skipped
5. Integration tests verify all three scenarios (valid, missing, invalid token)

## Technical Notes

- File: `src/main/java/auto/parto/config/JwtTokenAuthenticationFilter.java` line 44
- Current code: `return chain.filter(exchange);` — must change to `exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); return exchange.getResponse().setComplete();`
- The public endpoint check must also handle `/swagger-ui.html` and `/webjars/**` (currently only checks `/auth/` and `/v3/api-docs`)

## Risk

🔴 **CRITICAL** — The filter weakens the auth system. Although `SecurityConfig.authorizeExchange` still enforces auth, the broken filter pattern obscures the security model and is fragile against config changes.

## Dependencies

None.
