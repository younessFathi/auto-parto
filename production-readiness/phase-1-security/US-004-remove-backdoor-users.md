# US-004: Remove Hardcoded Test Users with Empty Passwords

**As a** security engineer  
**I want** the `ReactiveAuthenticationManagerConfig` to be removed or disabled in production  
**so that** no hardcoded `testuser` or `admin` accounts with empty passwords exist as backdoor authentication paths.

## Acceptance Criteria

1. `ReactiveAuthenticationManagerConfig.java` is removed or conditionally disabled when `spring.profiles.active` does not include `dev` or `test`
2. If the bean is kept for local development, it is annotated with `@Profile("dev | test")`
3. No production startup includes these users
4. The `PasswordEncoder` configuration (BCrypt) is preserved for real user authentication

## Technical Notes

- File: `src/main/java/auto/parto/config/ReactiveAuthenticationManagerConfig.java`
- Current bean creates `User.withUsername("testuser").roles("USER").password("").build()` and `User.withUsername("admin").roles("ADMIN").password("").build()`
- Spring Security's `MapReactiveUserDetailsService` is used as fallback — these users exist for any auth mechanism

## Risk

🔴 **CRITICAL** — Accounts with empty passwords accessible in all profiles, including production.

## Dependencies

None.
