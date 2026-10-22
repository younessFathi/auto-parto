# F02 Identity and Security

Create one canonical identity model and enforce authentication, roles, ownership, and secure verification.

**Status:** Done

- [AP-0004](../tickets/AP-0004-canonical-authentication.md): Establish the canonical authenticated principal.
- [AP-0005](../tickets/AP-0005-prevent-identity-impersonation.md): Fix caller-controlled identity and ownership authorization.
- [AP-0006](../tickets/AP-0006-secure-otp.md): Implement hashed, expiring, atomic single-use OTP challenges.
- [AP-0031](../tickets/AP-0031-security-authorization-integration-tests.md): Verify canonical authentication and sensitive-route authorization end to end.
- [AP-0032](../tickets/AP-0032-unify-controller-authorization.md): Unify controller authorization checks and canonical-principal handling.

## Completion Tracking

AP-0004, AP-0005, AP-0006, AP-0031, and AP-0032 are implemented and verified. F02 provides canonical principals, authenticated ownership derivation, consistent role and ownership authorization, hashed and bounded OTP challenges on PostgreSQL and Mongo paths, fail-closed authentication rate limiting, generic OTP errors, and end-to-end security-chain coverage.
