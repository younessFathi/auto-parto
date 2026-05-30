# US-022: Add Integration Tests for All Controllers

**As a** developer  
**I want** WebFlux integration tests for every controller endpoint  
**so that** HTTP status codes, response bodies, validation errors, and edge cases are verified for the entire API surface.

## Acceptance Criteria

1. Test coverage is added for the following currently untested controllers:
   - `AuthController` — send-otp-email, validate-otp, checkCustomerExisting, verify-gcp-token
   - `BuyerController` — deactivate
   - `SellerController` — create, getById, addFunds, deductFunds, deactivate
   - `PieceController` — getAllPiecesByCustomerId, getPieceDetailByPieceId (pagination validation already tested)
   - `ImageController` — listImages
   - `NotificationController` — subscribe, subscribeToUser, sendToUser, broadcast
   - `FcmNotificationController` — register, send, notifySearch, notifyResponse, urgent, stats, health
2. Tests use `@WebFluxTest` with mocked service layer for unit-level controller tests
3. At minimum, each endpoint has:
   - Happy path test (200/201)
   - Validation error test (400)
   - Authentication/authorization test (401/403)
4. Request/response serialization is verified (JSON field names, types)

## Technical Notes

- Files under `src/main/java/auto/parto/api/`
- Use `@WebFluxTest(controllers = XController.class)` with `@MockBean` for service dependencies
- Use `WebTestClient` for HTTP request/response assertions
- For SSE endpoints (`NotificationController`), use `WebTestClient` with `exchangeToFlux()` for streaming responses

## Risk

🟠 **HIGH** — 8 of 10 controllers have zero tests. Entire API surface is untested, including all auth and FCM flows.

## Dependencies

None.
