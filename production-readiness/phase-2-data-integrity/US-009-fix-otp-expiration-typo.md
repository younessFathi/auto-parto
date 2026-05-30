# US-009: Fix OTP Expiration Configuration Typo

**As** an operations engineer  
**I want** the `opt.expiration.minutes` property to be correctly named `otp.expiration.minutes`  
**so that** the OTP expiration duration configured in application YAML files is actually applied at runtime.

## Acceptance Criteria

1. `opt.expiration.minutes` is corrected to `otp.expiration.minutes` in `application.yml` and `application-dev.yml`
2. The `OtpDetails` model's expiration logic (which reads `otp.expiration.minutes`) now receives the configured value
3. Unit tests verify that the OTP expiration configuration is correctly injected
4. A fallback default value (e.g., 5 minutes) is present in case the property is not set

## Technical Notes

- Files: `src/main/resources/application.yml` line 41, `src/main/resources/application-dev.yml`
- The value `5` is intended as the OTP validity duration in minutes
- `OtpDetails.java` uses `@Value("${otp.expiration.minutes}")` — currently always gets the default (likely 0 or fails to inject)

## Risk

🟠 **HIGH** — OTP expiration is uncontrolled. Users may have unlimited time to use an OTP, or no time at all.

## Dependencies

None.
