\set ON_ERROR_STOP on
\getenv database_name PGDATABASE
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

SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'runtime_user', :'runtime_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'runtime_user') \gexec
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'migration_user', :'migration_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migration_user') \gexec

ALTER ROLE :"runtime_user" NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'runtime_password';
ALTER ROLE :"migration_user" NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'migration_password';
SELECT format('ALTER ROLE %I CONNECTION LIMIT %s', :'runtime_user', :'runtime_connection_limit') \gexec
SELECT format('ALTER ROLE %I CONNECTION LIMIT %s', :'migration_user', :'migration_connection_limit') \gexec
GRANT CONNECT ON DATABASE :"database_name" TO :"runtime_user", :"migration_user";
GRANT USAGE ON SCHEMA public TO :"runtime_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"migration_user";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"runtime_user";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"runtime_user";
SELECT set_config('app.migration_user', :'migration_user', false);

DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        EXECUTE format(
            'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.flyway_schema_history TO %I',
            current_setting('app.migration_user')
        );
    END IF;
END;
$$;

ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"runtime_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"runtime_user";

SELECT
    n.nspname AS schema_name,
    c.relname AS object_name,
    CASE c.relkind
        WHEN 'S' THEN 'sequence'
        WHEN 'p' THEN 'partitioned table'
        ELSE 'table'
    END AS object_type,
    pg_get_userbyid(c.relowner) AS current_owner,
    current_setting('app.migration_user') AS required_owner,
    c.relname = 'flyway_schema_history' AS is_flyway_history,
    CASE c.relkind
        WHEN 'S' THEN format('ALTER SEQUENCE %I.%I OWNER TO %I;', n.nspname, c.relname, current_setting('app.migration_user'))
        ELSE format('ALTER TABLE %I.%I OWNER TO %I;', n.nspname, c.relname, current_setting('app.migration_user'))
    END AS exact_manual_repair
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p', 'S')
  AND pg_get_userbyid(c.relowner) IS DISTINCT FROM current_setting('app.migration_user')
ORDER BY c.relkind, c.relname;

DO $$
DECLARE
    repairs text;
BEGIN
    SELECT string_agg(
        CASE c.relkind
            WHEN 'S' THEN format('ALTER SEQUENCE %I.%I OWNER TO %I;', n.nspname, c.relname, current_setting('app.migration_user'))
            ELSE format('ALTER TABLE %I.%I OWNER TO %I;', n.nspname, c.relname, current_setting('app.migration_user'))
        END,
        E'\n'
        ORDER BY c.relname
    )
    INTO repairs
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind IN ('r', 'p', 'S')
      AND c.relname <> 'flyway_schema_history'
      AND pg_get_userbyid(c.relowner) IS DISTINCT FROM current_setting('app.migration_user');

    IF repairs IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Existing public tables/sequences are not owned by the migration role; recovery stopped',
            DETAIL = repairs,
            HINT = 'An authorized DBA must review each listed object and execute only approved ALTER TABLE/SEQUENCE ... OWNER TO commands, then rerun this procedure. No global ownership reassignment was performed.';
    END IF;
END;
$$;
