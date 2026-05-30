# US-001: Revoke Exposed Secrets and Remove from Repository

**As a** security engineer  
**I want** all secrets committed in `.env` to be revoked and the file removed from git history  
Google OAuth Client Secret: [REDACTED_CREDENTIAL]

## Acceptance Criteria

1. `.env` is added to `.gitignore`
2. All secrets in `.env` have been revoked via their respective provider consoles and replaced with new values
3. Git history is purged of `.env` using `git filter-branch` or `bfg-repo-cleaner`
4. Team is notified of rotated credentials
5. `.env.example` is created with placeholder values and committed

## Technical Notes

Google Client ID: [REDACTED_CREDENTIAL]
Google Client Secret: [REDACTED_CREDENTIAL]
Twilio SID: [REDACTED_CREDENTIAL]
Twilio Auth Token: [REDACTED_CREDENTIAL]
Resend API Key: [REDACTED_CREDENTIAL]
- Revoke at: Google Cloud Console, Twilio Console, Resend Dashboard

## Risk

🔴 **CRITICAL** — Live credentials in public repository. Immediate revocation required.

## Dependencies

None.
