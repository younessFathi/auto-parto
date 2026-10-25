# Production Secrets Runbook

This runbook describes the application contract delivered by AP-0022. It does not make the deployment production-ready by itself.

## Delivery

Use an OpenBao agent or an approved protected deployment mechanism to provide the `prod` profile with short-lived, environment-scoped values. Set `APP_SECRET_PROVIDER=openbao`, an HTTPS `OPENBAO_ADDR`, the workload `OPENBAO_ROLE`, and `OPENBAO_AUTH_METHOD` (`kubernetes` or `jwt`). The workload identity must be bound to one production policy and must not be a root or operator token.

The policy should issue only the application PostgreSQL and Redis leases, object-storage credentials, and explicitly enabled provider credentials needed by this service. Keep audit database credentials in a separate policy and use independent audit roles.

## Rotation

Renew dynamic leases before expiry and restart or reload the application through the deployment platform when a static provider credential rotates. For JWT signing, publish the new verification key before issuing new tokens, accept the old key only during the configured maximum token lifetime, then revoke the old key. The current application still uses its existing symmetric JWT implementation; asymmetric key publication and overlap are an authentication and infrastructure follow-up, so this runbook does not claim that capability is complete.

## Failure behavior

The `prod` profile fails during startup when required values are missing, unresolved, local, malformed, or unsafe. Optional Firebase and email credentials are required only when their providers are enabled. Audit credentials are required when audit processing is enabled. Health endpoints do not disclose secret values or detailed configuration errors.

Before enabling production traffic, complete AP-0021, AP-0026, AP-0028, the OpenBao policy and backup procedures, and the F09 go-live checks.
