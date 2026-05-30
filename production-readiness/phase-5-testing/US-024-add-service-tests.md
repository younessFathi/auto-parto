# US-024: Add Unit Tests for Untested Service Classes

**As a** developer  
**I want** unit tests for all service classes that currently lack coverage  
**so that** business logic regressions are caught at the unit test level.

## Acceptance Criteria

Tests are added for:

1. **PieceService** — CRUD operations, caching behavior, cache eviction, missing piece scenarios
2. **PieceOfferService** — create, update, missing piece offer scenarios
3. **BuyerService** — create, deactivate, find by clerkId, OTP operations
4. **SellerService** — create, deactivate, find by email, OTP operations (addFunds already tested)
5. **FileManagerService** — file upload, allowed extensions, directory creation failure
6. **FileService** — save, findAllByIds, missing files
7. **BasicCircuitBreaker** — recordFailure opens circuit after threshold, recordSuccess resets, half-open state, concurrent safety
8. **BuyerAuthStrategy** — OTP creation for existing/non-existing buyers
9. **SellerAuthStrategy** — OTP creation for existing/non-existing sellers
10. **NotificationPreferencesService** — user preference caching

## Technical Notes

- Use `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks`
- Use `StepVerifier` for reactive type assertions
- Existing test patterns (`SellerServiceAddFundsTest`, `BuyerOtpValidationStrategyTest`) serve as templates

## Risk

🟠 **HIGH** — Core business logic (pieces, offers, buyers, sellers) has no test coverage. Refactoring these is unsafe.

## Dependencies

None.
