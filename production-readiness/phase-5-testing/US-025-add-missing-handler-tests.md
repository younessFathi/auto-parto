# US-025: Add Tests for Missing Exception Handler Cases

**As a** developer  
**I want** the `OtpExceptionHandler` to have tests covering all handled exception types  
**so that** error responses are verified for every error scenario and unhandled exceptions are detected.

## Acceptance Criteria

1. Tests are added for the following currently untested exception handlers:
   - `OtpExpiredException` → 410 Gone
   - `OtpTooManyRequestsException` → 429 Too Many Requests
   - `FileUploadException` → 500 Internal Server Error
   - `EntityNotFoundException` → 404 Not Found
   - `InvalidGCPToken` → 404 Not Found (or 401 after US-003 fix)
   - `InsufficientBalanceException` → 400 Bad Request
   - `WebExchangeBindException` → 400 Bad Request with field-level details
   - `ConstraintViolationException` → 400 Bad Request
   - `IllegalArgumentException` → 400 Bad Request
2. Tests verify that `ApiErrorResponse` includes:
   - `requestId` is present and matches the correlation ID
   - `timestamp` is present and valid
   - `status` matches the HTTP status
   - `error` and `message` are meaningful
3. Tests verify that generic `Exception` handler returns 500 with no sensitive information leakage
4. The `EmailSendException`, `NoActiveTokensException`, and `NoMatchigSellersFoundException` are either:
   - Added as explicit handlers, or
   - Verified that they correctly fall through to the generic 500 handler (documented behavior)

## Technical Notes

- File: `src/test/java/auto/parto/advicer/OtpExceptionHandlerTest.java`
- Use `@WebFluxTest` with `@Import(OtpExceptionHandler.class)` and mock controllers that throw each exception type
- The existing test file only covers `OtpInvalidException`, generic `Exception`, and `OtpLockedException`

## Risk

🟡 **MEDIUM** — 9 of 12 exception handlers are untested. The generic 500 handler does not log stack traces (noted in analysis).

## Dependencies

None.
