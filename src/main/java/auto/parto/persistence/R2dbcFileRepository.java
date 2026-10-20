package auto.parto.persistence;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.files", name = "enabled", havingValue = "true")
public class R2dbcFileRepository implements FileRepository {

    private static final String SELECT = """
            SELECT file.public_id, owner.public_id AS owner_public_id,
                   request.public_id AS request_public_id, listing.public_id AS listing_public_id,
                   file.object_key, file.original_filename, file.content_type, file.byte_size,
                   file.checksum, file.status, file.created_at, file.updated_at
            FROM files file
            JOIN app_users owner ON owner.id = file.owner_user_id
            LEFT JOIN part_requests request ON request.id = file.part_request_id
            LEFT JOIN part_listings listing ON listing.id = file.part_listing_id
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;

    public R2dbcFileRepository(DatabaseClient databaseClient, TransactionalOperator transactions) {
        this.databaseClient = databaseClient;
        this.transactions = transactions;
    }

    @Override
    public Mono<FileRecord> createForRequest(UUID ownerUserPublicId, UUID requestPublicId, String objectKey,
                                             String originalFilename, String contentType, long byteSize,
                                             String checksum) {
        return create(ownerUserPublicId, requestPublicId, null, objectKey, originalFilename, contentType,
                byteSize, checksum);
    }

    @Override
    public Mono<FileRecord> createForListing(UUID ownerUserPublicId, UUID listingPublicId, String objectKey,
                                             String originalFilename, String contentType, long byteSize,
                                             String checksum) {
        return create(ownerUserPublicId, null, listingPublicId, objectKey, originalFilename, contentType,
                byteSize, checksum);
    }

    private Mono<FileRecord> create(UUID ownerPublicId, UUID requestPublicId, UUID listingPublicId,
                                    String objectKey, String originalFilename, String contentType, long byteSize,
                                    String checksum) {
        requirePublicId(ownerPublicId, "ownerUserPublicId");
        if ((requestPublicId == null) == (listingPublicId == null)) {
            throw new IllegalArgumentException("exactly one file target is required");
        }
        requireText(objectKey, "objectKey");
        requireText(originalFilename, "originalFilename");
        requireText(contentType, "contentType");
        if (byteSize < 0) throw new IllegalArgumentException("byteSize cannot be negative");
        String targetColumn = requestPublicId != null ? "part_request_id" : "part_listing_id";
        String targetSql = requestPublicId != null
                ? "SELECT request.id FROM part_requests request "
                + "JOIN app_users request_owner ON request_owner.id = request.buyer_user_id "
                + "WHERE request.public_id = :targetPublicId AND request_owner.public_id = :ownerPublicId"
                : "SELECT listing.id FROM part_listings listing "
                + "JOIN app_users listing_owner ON listing_owner.id = listing.seller_user_id "
                + "WHERE listing.public_id = :targetPublicId AND listing_owner.public_id = :ownerPublicId";
        String sql = """
                INSERT INTO files (public_id, owner_user_id, %s, object_key, original_filename,
                                   content_type, byte_size, checksum)
                SELECT gen_random_uuid(), owner.id, target.id, :objectKey, :originalFilename,
                       :contentType, :byteSize, :checksum
                FROM app_users owner
                JOIN (%s) target ON TRUE
                WHERE owner.public_id = :ownerPublicId AND owner.status = 'ACTIVE'
                RETURNING public_id
                """.formatted(targetColumn, targetSql);
        return databaseClient.sql(sql)
                .bind("targetPublicId", requestPublicId != null ? requestPublicId : listingPublicId)
                .bind("ownerPublicId", ownerPublicId).bind("objectKey", objectKey)
                .bind("originalFilename", originalFilename).bind("contentType", contentType)
                .bind("byteSize", byteSize)
                .bind("checksum", checksum == null ? "" : checksum)
                .map((row, metadata) -> row.get("public_id", UUID.class)).one()
                .flatMap(this::findByPublicId).as(transactions::transactional);
    }

    @Override
    public Flux<FileRecord> findByRequest(UUID requestPublicId, int limit) {
        return findForTarget(requestPublicId, "request.public_id", limit);
    }

    @Override
    public Flux<FileRecord> findByListing(UUID listingPublicId, int limit) {
        return findForTarget(listingPublicId, "listing.public_id", limit);
    }

    @Override
    public Flux<FileRecord> findByPublicIds(List<UUID> filePublicIds, int limit) {
        if (filePublicIds == null || filePublicIds.isEmpty() || filePublicIds.size() > 100) {
            throw new IllegalArgumentException("filePublicIds must contain between 1 and 100 IDs");
        }
        requireLimit(limit);
        String placeholders = java.util.stream.IntStream.range(0, filePublicIds.size())
                .mapToObj(index -> ":filePublicId" + index)
                .collect(java.util.stream.Collectors.joining(", "));
        DatabaseClient.GenericExecuteSpec query = databaseClient.sql(SELECT +
                " WHERE file.public_id IN (" + placeholders + ") AND file.status <> 'TOMBSTONED' " +
                "ORDER BY file.created_at LIMIT :limit");
        for (int index = 0; index < filePublicIds.size(); index++) {
            requirePublicId(filePublicIds.get(index), "filePublicIds");
            query = query.bind("filePublicId" + index, filePublicIds.get(index));
        }
        return query.bind("limit", limit).map((row, metadata) -> map(row)).all();
    }

    @Override
    public Flux<FileRecord> findActive(int limit) {
        requireLimit(limit);
        return databaseClient.sql(SELECT
                        + " WHERE file.status = 'ACTIVE' ORDER BY file.created_at, file.public_id LIMIT :limit")
                .bind("limit", limit).map((row, metadata) -> map(row)).all();
    }

    @Override
    public Mono<Long> countActive() {
        return databaseClient.sql("SELECT COUNT(*) AS active_count FROM files WHERE status = 'ACTIVE'")
                .map((row, metadata) -> row.get("active_count", Long.class)).one();
    }

    @Override
    public Mono<Long> countActiveByOwner(UUID ownerUserPublicId) {
        requirePublicId(ownerUserPublicId, "ownerUserPublicId");
        return databaseClient.sql("""
                        SELECT COUNT(*) AS active_count FROM files file
                        JOIN app_users owner ON owner.id = file.owner_user_id
                        WHERE owner.public_id = :ownerPublicId AND file.status = 'ACTIVE'
                        """)
                .bind("ownerPublicId", ownerUserPublicId)
                .map((row, metadata) -> row.get("active_count", Long.class)).one();
    }

    private Flux<FileRecord> findForTarget(UUID targetPublicId, String targetColumn, int limit) {
        requirePublicId(targetPublicId, "targetPublicId");
        requireLimit(limit);
        return databaseClient.sql(SELECT + " WHERE " + targetColumn + " = :targetPublicId AND file.status <> 'TOMBSTONED' ORDER BY file.created_at LIMIT :limit")
                .bind("targetPublicId", targetPublicId).bind("limit", limit)
                .map((row, metadata) -> map(row)).all();
    }

    @Override
    public Mono<FileRecord> activate(UUID filePublicId, UUID ownerUserPublicId) {
        requirePublicId(filePublicId, "filePublicId");
        requirePublicId(ownerUserPublicId, "ownerUserPublicId");
        return transactions.transactional(findByPublicId(filePublicId)
                .filter(record -> ownerUserPublicId.equals(record.ownerUserPublicId()))
                .flatMap(existing -> {
                    if ("ACTIVE".equals(existing.status())) {
                        return Mono.just(existing);
                    }
                    if (!"PENDING".equals(existing.status())) {
                        return Mono.empty();
                    }
                    return insertAudit(filePublicId, ownerUserPublicId)
                            .flatMap(auditId -> databaseClient.sql("""
                                            UPDATE files SET status = 'ACTIVE', last_audit_record_id = :auditId
                                            WHERE public_id = :filePublicId
                                              AND owner_user_id = (SELECT id FROM app_users WHERE public_id = :ownerPublicId)
                                              AND status = 'PENDING'
                                            RETURNING public_id
                                            """)
                                    .bind("auditId", auditId).bind("filePublicId", filePublicId)
                                    .bind("ownerPublicId", ownerUserPublicId)
                                    .map((row, metadata) -> row.get("public_id", UUID.class)).one())
                            .flatMap(this::findByPublicId);
                }));
    }

    @Override
    public Mono<Boolean> tombstone(UUID filePublicId, UUID ownerUserPublicId) {
        requirePublicId(filePublicId, "filePublicId");
        requirePublicId(ownerUserPublicId, "ownerUserPublicId");
        return transactions.transactional(insertAudit(filePublicId, ownerUserPublicId)
                .flatMap(auditId -> databaseClient.sql("""
                                UPDATE files SET status = 'TOMBSTONED', last_audit_record_id = :auditId
                                WHERE public_id = :filePublicId
                                  AND owner_user_id = (SELECT id FROM app_users WHERE public_id = :ownerPublicId)
                                  AND status <> 'TOMBSTONED'
                                """)
                        .bind("auditId", auditId).bind("filePublicId", filePublicId)
                        .bind("ownerPublicId", ownerUserPublicId).fetch().rowsUpdated()
                        .map(rows -> rows == 1)));
    }

    private Mono<Long> insertAudit(UUID filePublicId, UUID ownerUserPublicId) {
        return databaseClient.sql("""
                        INSERT INTO audit_records (public_id, action, entity_type, entity_id)
                        SELECT gen_random_uuid(), 'UPDATE', 'files', id FROM files
                        WHERE public_id = :filePublicId
                          AND owner_user_id = (SELECT id FROM app_users WHERE public_id = :ownerPublicId)
                        RETURNING id
                        """)
                .bind("filePublicId", filePublicId).bind("ownerPublicId", ownerUserPublicId)
                .map((row, metadata) -> row.get("id", Long.class)).one();
    }

    @Override
    public Mono<FileRecord> findByPublicId(UUID publicId) {
        requirePublicId(publicId, "filePublicId");
        return databaseClient.sql(SELECT + " WHERE file.public_id = :publicId")
                .bind("publicId", publicId).map((row, metadata) -> map(row)).one();
    }

    private static FileRecord map(Row row) {
        return new FileRecord(row.get("public_id", UUID.class), row.get("owner_public_id", UUID.class),
                row.get("request_public_id", UUID.class), row.get("listing_public_id", UUID.class),
                row.get("object_key", String.class), row.get("original_filename", String.class),
                row.get("content_type", String.class), row.get("byte_size", Long.class),
                row.get("checksum", String.class), row.get("status", String.class),
                row.get("created_at", Instant.class), row.get("updated_at", Instant.class));
    }

    private static void requirePublicId(UUID value, String name) { if (value == null) throw new IllegalArgumentException(name + " is required"); }
    private static void requireText(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); }
    private static void requireLimit(int limit) { if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000"); }
}
