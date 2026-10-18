CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    schema_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_public_id UUID,
    subject_type VARCHAR(128),
    subject_public_id UUID,
    correlation_id UUID,
    source VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT audit_events_schema_version_chk CHECK (schema_version > 0),
    CONSTRAINT audit_events_payload_object_chk CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX audit_events_occurred_idx
    ON audit_events (occurred_at DESC, event_id);
CREATE INDEX audit_events_subject_idx
    ON audit_events (subject_type, subject_public_id, occurred_at DESC);
CREATE INDEX audit_events_actor_idx
    ON audit_events (actor_public_id, occurred_at DESC);
CREATE INDEX audit_events_type_idx
    ON audit_events (event_type, occurred_at DESC);
CREATE INDEX audit_events_correlation_idx
    ON audit_events (correlation_id, occurred_at DESC);

CREATE FUNCTION prevent_audit_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit events are append-only';
END;
$$;

CREATE TRIGGER audit_events_immutable_trigger
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_event_mutation();

DO $$
BEGIN
    CREATE ROLE audit_writer NOLOGIN;
EXCEPTION WHEN duplicate_object THEN NULL;
END;
$$;

DO $$
BEGIN
    CREATE ROLE audit_reader NOLOGIN;
EXCEPTION WHEN duplicate_object THEN NULL;
END;
$$;

REVOKE ALL ON audit_events FROM PUBLIC;
REVOKE ALL ON SEQUENCE audit_events_id_seq FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO audit_writer, audit_reader;
GRANT INSERT ON audit_events TO audit_writer;
GRANT USAGE, SELECT ON SEQUENCE audit_events_id_seq TO audit_writer;
GRANT SELECT ON audit_events TO audit_writer;
GRANT SELECT ON audit_events TO audit_reader;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_events FROM audit_writer, audit_reader;
