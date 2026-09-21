\set QUIET 1
\getenv database_name POSTGRES_DB
\getenv runtime_user APP_POSTGRES_RUNTIME_USER
\getenv runtime_password APP_POSTGRES_RUNTIME_PASSWORD
\getenv migration_user APP_POSTGRES_MIGRATION_USER
\getenv migration_password APP_POSTGRES_MIGRATION_PASSWORD

\o /dev/null
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'runtime_user', :'runtime_password'
) \gexec
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'migration_user', :'migration_password'
) \gexec
\o

GRANT CONNECT ON DATABASE :"database_name" TO :"runtime_user", :"migration_user";
GRANT USAGE ON SCHEMA public TO :"runtime_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"migration_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"runtime_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"runtime_user";
