# US-027: Fix Contradictory @Document Annotations on Embedded Models

**As a** developer  
**I want** the `Address` and `OtpDetails` models to have their `@Document` annotations either removed or aligned with actual usage  
**so that** the data model accurately reflects how documents are stored and there is no confusion about collection mapping.

## Acceptance Criteria

1. **Address.java** — If `Address` is always embedded inside `Seller`:
   - Remove `@Document(collection = "addresses")` annotation
   - Remove `@Id` field (or keep if used for in-Java identity, but remove MongoDB mapping)
   - OR: keep as a standalone collection if independent address storage is planned
2. **OtpDetails.java** — If `OtpDetails` is always embedded inside `Buyer` and `Seller`:
   - Remove `@Document(collection = "otpDetails")` annotation
   - Remove `UUID id` field if unused
3. All mapper and service code continues to work correctly after annotation changes
4. No data migration is needed (embedded documents are not stored in their own collections)

## Technical Notes

- Files: `Address.java`, `OtpDetails.java`
- Both have `@Document(collection = "...")` but are always embedded via `@Field` in `Seller` and `Buyer`
- MongoDB does not enforce collection mapping for embedded documents, but the annotations are misleading for code reviewers
- If there is any code that saves `Address` or `OtpDetails` standalone (via a repository), that code will be affected — verify none exists

## Risk

🟢 **LOW** — No functional impact. These annotations are currently misleading but do not cause runtime errors since embedded documents ignore `@Document`.

## Dependencies

None.
