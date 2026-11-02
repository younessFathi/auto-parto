\set QUIET 1
\getenv database_name POSTGRES_DB
\getenv runtime_user APP_POSTGRES_RUNTIME_USER
\getenv runtime_password APP_POSTGRES_RUNTIME_PASSWORD
\getenv migration_user APP_POSTGRES_MIGRATION_USER
\getenv migration_password APP_POSTGRES_MIGRATION_PASSWORD
\getenv runtime_connection_limit APP_POSTGRES_RUNTIME_CONNECTION_LIMIT
\getenv migration_connection_limit APP_POSTGRES_MIGRATION_CONNECTION_LIMIT

SELECT CASE
    WHEN :'runtime_connection_limit' ~ '^[1-9][0-9]{0,2}$' THEN :'runtime_connection_limit'::integer BETWEEN 1 AND 100
    ELSE false
END AS runtime_limit_valid,
CASE
    WHEN :'migration_connection_limit' ~ '^[1-9][0-9]{0,2}$' THEN :'migration_connection_limit'::integer BETWEEN 1 AND 10
    ELSE false
END AS migration_limit_valid
\gset
\if :runtime_limit_valid
\else
\echo 'APP_POSTGRES_RUNTIME_CONNECTION_LIMIT must be between 1 and 100'
\quit 3
\endif
\if :migration_limit_valid
\else
\echo 'APP_POSTGRES_MIGRATION_CONNECTION_LIMIT must be between 1 and 10'
\quit 3
\endif

\o /dev/null
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT %s',
    :'runtime_user', :'runtime_password', :'runtime_connection_limit'
) \gexec
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT %s',
    :'migration_user', :'migration_password', :'migration_connection_limit'
) \gexec
\o

GRANT CONNECT ON DATABASE :"database_name" TO :"runtime_user", :"migration_user";
GRANT USAGE ON SCHEMA public TO :"runtime_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"migration_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"runtime_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"runtime_user";
