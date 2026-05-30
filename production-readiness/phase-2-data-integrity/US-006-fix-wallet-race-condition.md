# US-006: Fix Wallet Balance Race Condition with Atomic Update

**As a** seller  
**I want** my wallet balance to be correctly updated even when multiple transactions occur simultaneously  
**so that** concurrent deposits or deductions do not overwrite each other and my balance remains accurate.

## Acceptance Criteria

1. `SellerService.addFunds()` uses MongoDB's `$inc` operator with `findAndModify` instead of read-then-write
2. Two concurrent `addFunds` calls result in correct cumulative balance (not just the last write)
3. `InsufficientBalanceException` is properly thrown via `Mono.error()` (not raw `throw`) when the balance would go negative
4. The circuit breaker / retry logic in the service layer still applies correctly
5. Unit and integration tests verify concurrent balance updates

## Technical Notes

- File: `src/main/java/auto/parto/service/SellerService.java` lines 47-59
- Current pattern: load `Seller` → `wallet.addFunds(amount)` → `sellerRepository.save(seller)`
- Fix: use `reactiveMongoTemplate.findAndModify` with `Update().inc("wallet.balance", amount)` and validate the result
- Also fix line 56: change `throw new InsufficientBalanceException(...)` to `return Mono.error(new InsufficientBalanceException(...))`

## Risk

🔴 **CRITICAL** — Financial data corruption under concurrent load.

## Dependencies

None.
