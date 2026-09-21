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

if $compose run --rm \
        -e FLYWAY_URL=jdbc:postgresql://app-postgresql:5432/missing_database \
        -e FLYWAY_CONNECT_RETRIES=0 \
        app-postgresql-migrate; then
    echo "Expected the invalid database target to fail" >&2
    exit 1
fi

echo "Compose PostgreSQL migration smoke test passed, including failure stop behavior."
