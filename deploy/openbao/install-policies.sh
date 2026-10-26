#!/usr/bin/env bash
set -euo pipefail

# Run only from the protected OpenBao operations host with a short-lived
# operator token in its approved credential store. Never use a root token.
: "${BAO_TOKEN_FILE:?set protected non-root operator token file}"
export BAO_TOKEN="$(cat "$BAO_TOKEN_FILE")"
unset BAO_TOKEN_FILE

root="$(cd "$(dirname "$0")" && pwd)"
bao policy write production-app "$root/policies/app.hcl"
bao policy write production-audit "$root/policies/audit.hcl"
bao policy write production-migration "$root/policies/migration.hcl"
bao policy write staging-app "$root/policies/staging-app.hcl"
bao policy write staging-audit "$root/policies/staging-audit.hcl"
bao policy write staging-migration "$root/policies/staging-migration.hcl"

for role in production-app production-audit production-migration staging-app staging-audit staging-migration; do
  case "$role" in
    production-app) policy=production-app ;;
    production-audit) policy=production-audit ;;
    production-migration) policy=production-migration ;;
    staging-app) policy=staging-app ;;
    staging-audit) policy=staging-audit ;;
    staging-migration) policy=staging-migration ;;
  esac
  bao write "auth/approle/role/$role" token_policies="$policy" token_ttl=15m token_max_ttl=1h secret_id_ttl=10m secret_id_num_uses=1 bind_secret_id=true
done
