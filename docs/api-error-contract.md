# API Error Contract

All handled API failures return the same JSON shape:

```json
{
  "timestamp": "2026-10-14T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "path": "/api/resource",
  "requestId": "correlation-id",
  "details": {
    "field": "must be valid"
  }
}
```

`code` is stable for client handling. `message` is safe for display and never contains exception details, credentials, tokens, OTPs, or provider payloads. `requestId` is returned when the request correlation filter is active and can be supplied to support for diagnosis.

| Category | HTTP status | Example codes |
| --- | ---: | --- |
| Invalid input | 400 | `INVALID_REQUEST`, `VALIDATION_ERROR`, `INVALID_FILE_UPLOAD` |
| Authentication failure | 401 | `AUTHENTICATION_REQUIRED`, `AUTHENTICATION_FAILED` |
| Authorization failure | 403 | `ACCESS_DENIED` |
| Missing resource | 404 | `RESOURCE_NOT_FOUND` |
| State or idempotency conflict | 409 | `CONFLICT` |
| Unexpected failure | 500 | `INTERNAL_ERROR` |

Validation failures may include field-level messages in `details`. Asynchronous endpoints use `202 Accepted` in their normal success response; the error contract applies when those requests fail.
