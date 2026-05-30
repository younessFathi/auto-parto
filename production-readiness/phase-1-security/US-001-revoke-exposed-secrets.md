# US-001: Revoke Exposed Secrets and Remove from Repository

**As a** security engineer  
**I want** all secrets committed in `.env` to be revoked and the file removed from git history  
**so that** compromised credentials (Google OAuth Client Secret, Twilio Auth Token, Resend API Key) cannot be used by attackers.

## Acceptance Criteria

1. `.env` is added to `.gitignore`
2. All secrets in `.env` have been revoked via their respective provider consoles and replaced with new values
3. Git history is purged of `.env` using `git filter-branch` or `bfg-repo-cleaner`
4. Team is notified of rotated credentials
5. `.env.example` is created with placeholder values and committed

## Technical Notes

- Google Client ID: `[REDACTED_GOOGLE_CLIENT_ID]`
- Google Client Secret: `[REDACTED_GOOGLE_CLIENT_SECRET]`
- Twilio SID: `[REDACTED_TWILIO_ACCOUNT_SID]`
- Twilio Auth Token: `[REDACTED_CREDENTIAL]`
- Resend API Key: `[REDACTED_RESEND_API_KEY]`
- Revoke at: Google Cloud Console, Twilio Console, Resend Dashboard

## Risk

🔴 **CRITICAL** — Live credentials in public repository. Immediate revocation required.

## Dependencies

None.
