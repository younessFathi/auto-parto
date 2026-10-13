#!/usr/bin/env sh
set -eu

: "${APP_POSTGRES_BOOTSTRAP_PASSWORD:?set APP_POSTGRES_BOOTSTRAP_PASSWORD}"
: "${APP_POSTGRES_RUNTIME_PASSWORD:?set APP_POSTGRES_RUNTIME_PASSWORD}"
: "${APP_POSTGRES_MIGRATION_USER:?set APP_POSTGRES_MIGRATION_USER}"
: "${APP_POSTGRES_MIGRATION_PASSWORD:?set APP_POSTGRES_MIGRATION_PASSWORD}"

project="ap0003-smoke-$$"
compose="docker compose --project-name $project --profile postgres"

cleanup() {
    $compose down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

$compose up -d app-postgresql
$compose run --rm app-postgresql-migrate
$compose run --rm --no-deps app-postgresql-fixtures
$compose run --rm --no-deps app-postgresql-fixtures

$compose run --rm --no-deps \
    -v "$(pwd)/deploy/postgres/operations:/operations:ro" \
    app-postgresql psql --set ON_ERROR_STOP=1 --file /operations/reconcile-development-fixtures.sql

fixture_counts="$($compose exec -T \
    -e PGPASSWORD="$APP_POSTGRES_RUNTIME_PASSWORD" \
    app-postgresql psql \
    --username "${APP_POSTGRES_RUNTIME_USER:-auto_parto_runtime}" \
    --dbname "${APP_POSTGRES_DB:-auto_parto}" \
    --tuples-only --no-align \
    --command "SELECT (SELECT count(*) FROM app_users WHERE key_version = 'dev-fixture-v1'), (SELECT count(*) FROM buyer_profiles WHERE public_id = '00000000-0000-4000-8000-000000000201'), (SELECT count(*) FROM seller_profiles WHERE public_id = '00000000-0000-4000-8000-000000000202'), (SELECT count(*) FROM files)")"

if [ "$fixture_counts" != "2|1|1|0" ]; then
    echo "Expected idempotent PostgreSQL identity fixtures, got: $fixture_counts" >&2
    exit 1
fi

if $compose run --rm \
        -e FLYWAY_URL=jdbc:postgresql://app-postgresql:5432/missing_database \
        -e FLYWAY_CONNECT_RETRIES=0 \
        app-postgresql-migrate; then
    echo "Expected the invalid database target to fail" >&2
    exit 1
fi

echo "Compose PostgreSQL migration and fixture smoke test passed, including idempotency and failure stop behavior."
