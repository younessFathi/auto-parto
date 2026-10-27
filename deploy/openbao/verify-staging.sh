#!/usr/bin/env bash
set -euo pipefail

: "${BAO_ADDR:?set TLS staging OpenBao address}"
: "${BAO_CA_CERT:?set CA certificate path}"
: "${BAO_OPERATOR_TOKEN_FILE:?set path to short-lived non-root verification token}"
: "${APP_TOKEN_FILE:?set staging app token file}"
: "${AUDIT_TOKEN_FILE:?set staging audit token file}"
: "${MIGRATION_TOKEN_FILE:?set staging migration token file}"

export BAO_ADDR BAO_CACERT="$BAO_CA_CERT"
export BAO_TOKEN
BAO_TOKEN="$(cat "$BAO_OPERATOR_TOKEN_FILE")"
unset BAO_OPERATOR_TOKEN_FILE BAO_CA_CERT

expect_capability() {
  local token_file="$1" path="$2" expected="$3" got
  export BAO_TOKEN="$(cat "$token_file")"
  got="$(bao token capabilities "$path")"
  [[ "$got" == "$expected" ]] || { printf 'unexpected capability for %s\n' "$path" >&2; exit 1; }
}

expect_capability "$APP_TOKEN_FILE" kv/data/staging/application/runtime read
expect_capability "$APP_TOKEN_FILE" kv/data/production/application/runtime deny
expect_capability "$APP_TOKEN_FILE" kv/data/staging/audit/runtime deny
expect_capability "$AUDIT_TOKEN_FILE" kv/data/staging/audit/runtime read
expect_capability "$AUDIT_TOKEN_FILE" kv/data/staging/application/runtime deny
expect_capability "$MIGRATION_TOKEN_FILE" database/creds/staging-migration read
expect_capability "$MIGRATION_TOKEN_FILE" kv/data/staging/application/runtime deny

printf 'Staging policy boundary checks passed. Run credential/recovery drill from the protected operations host.\n'
