\set ON_ERROR_STOP on
\getenv probe_table AP0003_MIGRATION_PROBE_TABLE

DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RAISE EXCEPTION 'Flyway schema history does not exist; run the baseline migration before recovery';
    END IF;
    IF NOT has_table_privilege(current_user, 'public.flyway_schema_history', 'SELECT')
       OR NOT has_table_privilege(current_user, 'public.flyway_schema_history', 'INSERT')
       OR NOT has_table_privilege(current_user, 'public.flyway_schema_history', 'UPDATE') THEN
        RAISE EXCEPTION 'Migration role cannot read, insert, and update public.flyway_schema_history';
    END IF;
END;
$$;

BEGIN;
UPDATE public.flyway_schema_history
SET description = description
WHERE installed_rank = (SELECT min(installed_rank) FROM public.flyway_schema_history);
CREATE TABLE :"probe_table" (id BIGINT PRIMARY KEY);
ALTER TABLE :"probe_table" ADD COLUMN probe_value TEXT;
DROP TABLE :"probe_table";
ROLLBACK;
