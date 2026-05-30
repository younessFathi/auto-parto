# US-008: Add Missing MongoDB Indexes for Common Queries

**As a** platform engineer  
**I want** MongoDB indexes to exist on all fields used in query predicates  
**so that** the database does not perform full collection scans on high-traffic queries.

## Acceptance Criteria

1. `@Indexed` is added to the following fields:
   - `Buyer.email`
   - `Buyer.phoneNumber`
   - `Buyer.clerkId`
   - `Seller.email`
   - `File.pieceId`
   - `NotificationPreferences.userId`
2. Compound indexes are considered for multi-field query patterns:
   - `Seller(email, isActive)`
   - `Buyer(email, isActive)`
3. `MongoIndexesConsistencyTest` is updated to verify the new indexes
4. Index creation is verified in integration tests using Testcontainers

## Technical Notes

- Files: `Buyer.java`, `Seller.java`, `File.java`, `NotificationPreferences.java`
- `Piece.java` and `PieceOffer.java` already have correct compound indexes
- Dynamic field queries in `BuyerRepository.findBy(value, field)` and `SellerRepository.findBy(value, field)` cannot effectively use indexes — consider replacing with named methods

## Risk

🟠 **HIGH** — Performance degradation and potential timeouts on large collections.

## Dependencies

US-007 (File.pieceId field fix)
