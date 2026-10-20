# File storage operations

F06 stores file bytes through the `FileStorage` port and stores only the object key, checksum, size, content type, ownership target, and lifecycle state in PostgreSQL. Uploads are staged before provider publication and move through `PENDING`, `ACTIVE`, and `TOMBSTONED`. Finalization is owner-guarded and idempotent. Provider deletion runs on the bounded reactive scheduler after a failed or completed staged upload.

The upload boundary validates the extension, declared MIME type, signature, configured byte limit, decoded image dimensions, SHA-256 checksum, and the bounded active-file quota for the authenticated owner. The controller derives ownership from the authenticated principal and the repository repeats the target ownership check before creating metadata.

## Reconciliation

When the PostgreSQL cutover storage gate is enabled, reconciliation scans at most `APP_POSTGRES_CUTOVER_STORAGE_RECONCILIATION_MAX_FILES` active metadata rows and the same bounded number of provider keys. Missing objects keep readiness unhealthy. Keys without active metadata are reported as orphans. The default policy is report-only; set `APP_POSTGRES_CUTOVER_STORAGE_RECONCILIATION_DELETE_ORPHANS=true` only for an operator-approved cleanup deployment. Cleanup is bounded, sequential, and safe to repeat because provider deletion is idempotent.

## SeaweedFS deployment contract

The production provider is the S3-compatible SeaweedFS gateway. Put the gateway behind private networking and TLS, use a default-deny IAM policy with a bucket-scoped application identity, and supply access keys and server-side encryption configuration through the approved secret system. Persist both volume data and filer metadata on replicated storage in separate failure domains. Replication improves availability but is not a backup.

Backups must be off-cluster and include volume data, filer metadata, IAM policy state, encryption key references, and the object version/deletion policy. Restore in this order: secret and encryption-key availability, filer metadata, volume data, IAM policy, gateway routing, then application readiness. After any restore or volume replacement, run the bounded reconciliation gate and keep traffic stopped while it reports missing objects or capacity overflow. A repeatable drill should record the backup identifier, restore order, failed-volume simulation, reconciliation report, and final readiness result without recording credentials.
