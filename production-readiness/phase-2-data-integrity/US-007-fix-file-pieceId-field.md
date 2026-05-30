# US-007: Fix Mismatch Between File Model and Repository Query

**As a** developer  
**I want** the `File` model to have a `pieceId` field or the `FileRepository.getFilesByPieceId()` query to be corrected  
**so that** queries for files by piece ID actually return results instead of always returning empty.

## Acceptance Criteria

1. The `File` model (`auto.parto.model.File`) either:
   - (a) Gains a `pieceId` field (preferred), or
   - (b) The query `getFilesByPieceId` is changed to query by a field that exists
2. All existing files in the database that have a `pieceId` field (stored but unmapped) are now properly deserialized
3. `FileRepository.findAllByIds()` is renamed if it actually queries by `_id` (not `pieceId`)
4. Unit tests verify the mapping

## Technical Notes

- File: `src/main/java/auto/parto/repository/mongo_repository/FileRepository.java`
- Query: `Criteria.where("pieceId").is(pieceId)` — the model has NO `pieceId` Java field
- The `File` model also shadows `java.io.File` — consider renaming to `FileRecord` or `AppFile`

## Risk

🔴 **CRITICAL** — Feature is completely broken. Any code path that calls `getFilesByPieceId()` gets empty results.

## Dependencies

None.
