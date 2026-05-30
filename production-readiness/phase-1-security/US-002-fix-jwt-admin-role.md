# US-002: Fix JWT Token Generation to Stop Hardcoding ADMIN Role

**As a** security engineer  
**I want** `JwtTokenGenerator.generateToken()` to accept a role parameter instead of hardcoding `"ADMIN"`  
**so that** users authenticating via Google OAuth do not automatically receive administrative privileges.

## Acceptance Criteria

1. `generateToken(String value)` is updated to `generateToken(String value, String role)` or a new method signature
2. All callers are updated to pass the correct role
3. Existing users who received an ADMIN token via Google OAuth have their tokens revoked (if possible) or the fix deployed before any such users exist
4. `getAuthentication()` correctly reads the role claim from both token generation paths
5. Unit tests verify that each customer type (BUYER, SELLER) receives the correct role claim

## Technical Notes

- File: `src/main/java/auto/parto/utils/JwtTokenGenerator.java` line 116
- Current code: `.claim("role", "ADMIN")`
- The `getAuthentication()` method already reads from `"roles"` claim key — ensure consistency between the two claim keys (`"role"` vs `"roles"`)
- Two generation methods exist: `encode(value, user, type)` and `generateToken(value)` — only `generateToken` is broken

## Risk

🔴 **CRITICAL** — Privilege escalation vulnerability. Any Google-authenticated user is currently an admin.

## Dependencies

None.
