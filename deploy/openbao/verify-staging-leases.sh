#!/usr/bin/env bash
set -euo pipefail
set +x

: "${BAO_ADDR:?set TLS staging OpenBao address}"
: "${BAO_CA_CERT:?set CA certificate path}"
: "${BAO_OPERATOR_TOKEN_FILE:?set short-lived non-root drill-operator token file}"
: "${BAO_APP_TOKEN_FILE:?set staging app workload token file}"
: "${STAGING_PG_HOST:?set isolated staging PostgreSQL host}"
: "${STAGING_PG_PORT:?set isolated staging PostgreSQL port}"
: "${STAGING_PG_DATABASE:?set isolated staging database}"
: "${STAGING_PG_CA_FILE:?set PostgreSQL CA file}"

export BAO_ADDR BAO_CACERT="$BAO_CA_CERT"
operator_token="$(cat "$BAO_OPERATOR_TOKEN_FILE")"
app_token="$(cat "$BAO_APP_TOKEN_FILE")"
export BAO_TOKEN="$app_token"
old_lease=""
new_lease=""

cleanup() {
  set +e
  export BAO_TOKEN="$operator_token"
  [[ -z "$old_lease" ]] || bao lease revoke "$old_lease" >/dev/null 2>&1
  [[ -z "$new_lease" ]] || bao lease revoke "$new_lease" >/dev/null 2>&1
}
trap cleanup EXIT

issue_credentials() {
  local data
  data="$(bao read -format=json database/creds/staging-application)"
  LEASE_ID="$(jq -r '.lease_id' <<<"$data")"
  PGUSER="$(jq -r '.data.username' <<<"$data")"
  PGPASSWORD="$(jq -r '.data.password' <<<"$data")"
  [[ -n "$LEASE_ID" && "$LEASE_ID" != null && -n "$PGUSER" && "$PGUSER" != null && -n "$PGPASSWORD" && "$PGPASSWORD" != null ]]
}

db_probe() {
  PGPASSWORD="$2" PGUSER="$1" psql "host=$STAGING_PG_HOST port=$STAGING_PG_PORT dbname=$STAGING_PG_DATABASE sslmode=verify-full sslrootcert=$STAGING_PG_CA_FILE" \
    --no-psqlrc --tuples-only --command='SELECT 1' >/dev/null 2>&1
}

issue_credentials
old_lease="$LEASE_ID" old_user="$PGUSER" old_password="$PGPASSWORD"
db_probe "$old_user" "$old_password"

issue_credentials
new_lease="$LEASE_ID" new_user="$PGUSER" new_password="$PGPASSWORD"
db_probe "$new_user" "$new_password"

export BAO_TOKEN="$operator_token"
bao lease revoke "$old_lease" >/dev/null
if db_probe "$old_user" "$old_password"; then
  printf 'Old database credential still authenticates after lease revocation.\n' >&2
  exit 1
fi
db_probe "$new_user" "$new_password"
bao lease revoke "$new_lease" >/dev/null

export BAO_TOKEN="$app_token"
bao token renew -self >/dev/null
bao token revoke -self >/dev/null
if bao read -format=json kv/data/staging/application/runtime >/dev/null 2>&1; then
  printf 'Revoked workload token retained access.\n' >&2
  exit 1
fi

unset BAO_TOKEN operator_token app_token old_password new_password PGPASSWORD
printf 'Staging database lease rotation, old-credential rejection, and workload-token renewal/revocation passed.\n'
