#!/usr/bin/env bash
set -euo pipefail

: "${APP_POSTGRES_CUTOVER_ENABLED:?set APP_POSTGRES_CUTOVER_ENABLED=true for an application cutover}"
: "${APP_POSTGRES_MONGO_DISABLED:?set APP_POSTGRES_MONGO_DISABLED=true after Mongo traffic is removed}"
: "${APP_POSTGRES_CUTOVER_API_CONTRACTS_READY:?set APP_POSTGRES_CUTOVER_API_CONTRACTS_READY=true after API verification}"
: "${APP_POSTGRES_CUTOVER_STORAGE_RECONCILIATION_ENABLED:?set APP_POSTGRES_CUTOVER_STORAGE_RECONCILIATION_ENABLED=true}"
: "${APP_POSTGRES_BACKUP_VERIFIED_AT:?record the verified PostgreSQL backup time}"
: "${APP_POSTGRES_BACKUP_REFERENCE:?record the approved PostgreSQL backup or restore reference}"

test "$APP_POSTGRES_CUTOVER_ENABLED" = true
test "$APP_POSTGRES_MONGO_DISABLED" = true
test "$APP_POSTGRES_CUTOVER_API_CONTRACTS_READY" = true
test "$APP_POSTGRES_CUTOVER_STORAGE_RECONCILIATION_ENABLED" = true

if [ "${APP_FILE_STORAGE_PROVIDER:-local}" = s3 ]; then
  : "${APP_FILE_STORAGE_S3_ENDPOINT:?set the S3-compatible endpoint for cutover}"
  : "${APP_FILE_STORAGE_S3_BUCKET:?set the S3-compatible bucket for cutover}"
  : "${APP_FILE_STORAGE_S3_ACCESS_KEY:?set the S3-compatible access key through protected variables}"
  : "${APP_FILE_STORAGE_S3_SECRET_KEY:?set the S3-compatible secret key through protected variables}"
fi

echo "PostgreSQL cutover preflight passed with a verified backup attestation."
