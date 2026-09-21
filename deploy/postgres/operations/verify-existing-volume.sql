\set ON_ERROR_STOP on
\getenv runtime_user APP_POSTGRES_RUNTIME_USER
\getenv migration_user APP_POSTGRES_MIGRATION_USER
SELECT set_config('app.runtime_user', :'runtime_user', false);

SELECT
    rolname,
    rolsuper = false AS not_superuser,
    rolcreatedb = false AS cannot_create_database,
    rolcreaterole = false AS cannot_create_roles
FROM pg_roles
WHERE rolname IN (:'runtime_user', :'migration_user')
ORDER BY rolname;

SELECT :'runtime_user' AS runtime_user,
       has_schema_privilege(:'runtime_user', 'public', 'USAGE') AS runtime_schema_usage,
       has_schema_privilege(:'runtime_user', 'public', 'CREATE') AS runtime_schema_create;

SELECT :'migration_user' AS migration_user,
       has_schema_privilege(:'migration_user', 'public', 'USAGE') AS migration_schema_usage,
       has_schema_privilege(:'migration_user', 'public', 'CREATE') AS migration_schema_create;

SELECT :'migration_user' AS migration_user,
       has_table_privilege(:'migration_user', 'public.flyway_schema_history', 'SELECT') AS migration_history_select,
       has_table_privilege(:'migration_user', 'public.flyway_schema_history', 'INSERT') AS migration_history_insert,
       has_table_privilege(:'migration_user', 'public.flyway_schema_history', 'UPDATE') AS migration_history_update
WHERE to_regclass('public.flyway_schema_history') IS NOT NULL;

SELECT defaclrole::regrole AS object_owner,
       defaclnamespace::regnamespace AS schema_name,
       defaclacl
FROM pg_default_acl
WHERE defaclnamespace = 'public'::regnamespace
  AND defaclrole = :'migration_user'::regrole;

SELECT
    n.nspname AS schema_name,
    c.relname AS object_name,
    CASE c.relkind WHEN 'S' THEN 'sequence' ELSE 'table' END AS object_type,
    pg_get_userbyid(c.relowner) AS owner,
    has_table_privilege(:'runtime_user', format('%I.%I', n.nspname, c.relname), 'SELECT') AS runtime_select,
    has_table_privilege(:'runtime_user', format('%I.%I', n.nspname, c.relname), 'INSERT') AS runtime_insert,
    has_table_privilege(:'runtime_user', format('%I.%I', n.nspname, c.relname), 'UPDATE') AS runtime_update,
    has_table_privilege(:'runtime_user', format('%I.%I', n.nspname, c.relname), 'DELETE') AS runtime_delete,
    CASE WHEN c.relkind = 'S' THEN
        has_sequence_privilege(:'runtime_user', format('%I.%I', n.nspname, c.relname), 'USAGE')
    END AS runtime_sequence_usage,
    CASE WHEN c.relkind = 'S' THEN
        has_sequence_privilege(:'runtime_user', format('%I.%I', n.nspname, c.relname), 'SELECT')
    END AS runtime_sequence_select
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p', 'S')
ORDER BY c.relkind, c.relname;

DO $$
DECLARE
    missing text;
BEGIN
    SELECT string_agg(format('%I.%I', n.nspname, c.relname), ', ' ORDER BY c.relname)
    INTO missing
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind IN ('r', 'p')
      AND NOT (
          has_table_privilege(current_setting('app.runtime_user'), format('%I.%I', n.nspname, c.relname), 'SELECT')
          AND has_table_privilege(current_setting('app.runtime_user'), format('%I.%I', n.nspname, c.relname), 'INSERT')
          AND has_table_privilege(current_setting('app.runtime_user'), format('%I.%I', n.nspname, c.relname), 'UPDATE')
          AND has_table_privilege(current_setting('app.runtime_user'), format('%I.%I', n.nspname, c.relname), 'DELETE')
      );
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Runtime role % lacks required DML privileges on: %', current_setting('app.runtime_user'), missing;
    END IF;

    SELECT string_agg(format('%I.%I', n.nspname, c.relname), ', ' ORDER BY c.relname)
    INTO missing
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'S'
      AND NOT (
          has_sequence_privilege(current_setting('app.runtime_user'), format('%I.%I', n.nspname, c.relname), 'USAGE')
          AND has_sequence_privilege(current_setting('app.runtime_user'), format('%I.%I', n.nspname, c.relname), 'SELECT')
      );
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Runtime role % lacks required sequence privileges on: %', current_setting('app.runtime_user'), missing;
    END IF;
END;
$$;
