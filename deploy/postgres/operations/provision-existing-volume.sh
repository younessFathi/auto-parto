#!/usr/bin/env sh
set -eu

: "${PGHOST:?set PGHOST to the existing PostgreSQL host}"
: "${PGDATABASE:?set PGDATABASE to the application database}"
: "${PGUSER:?set PGUSER to an authorized bootstrap/DBA account}"
: "${PGPASSWORD:?set PGPASSWORD through a protected shell/session}"
: "${APP_POSTGRES_RUNTIME_USER:?set the intended runtime role name}"
: "${APP_POSTGRES_RUNTIME_PASSWORD:?set the runtime role password through a protected shell/session}"
: "${APP_POSTGRES_MIGRATION_USER:?set the intended migration role name}"
: "${APP_POSTGRES_MIGRATION_PASSWORD:?set the migration role password through a protected shell/session}"

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
psql --set=ON_ERROR_STOP=1 -f "$script_dir/provision-existing-volume.sql"
psql --set=ON_ERROR_STOP=1 -f "$script_dir/verify-existing-volume.sql"

probe_table="ap0003_migration_probe_$(date +%s)_$$"
PGUSER="$APP_POSTGRES_MIGRATION_USER" \
PGPASSWORD="$APP_POSTGRES_MIGRATION_PASSWORD" \
AP0003_MIGRATION_PROBE_TABLE="$probe_table" \
psql --set=ON_ERROR_STOP=1 -f "$script_dir/verify-migration-role.sql"

echo "Role provisioning and verification completed. Run Flyway only after reviewing the verification output."
