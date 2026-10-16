package auto.parto.advicer;

import auto.parto.config.CorrelationIdWebFilter;
import auto.parto.dto.ApiErrorResponse;
import auto.parto.exception.ConflictException;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.exception.FileUploadException;
import auto.parto.exception.InvalidGCPToken;
import auto.parto.exception.InsufficientBalanceException;
import auto.parto.exception.OtpInvalidException;
import auto.parto.exception.OtpLockedException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class OtpExceptionHandlerTest {

    @Test
    void buildsStandardizedErrorResponseWithRequestIdAndPath() {
        OtpExceptionHandler handler = new OtpExceptionHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/validate-otp/1234").build()
        );
        exchange.getAttributes().put(CorrelationIdWebFilter.REQUEST_ID_ATTRIBUTE, "req-789");

        ResponseEntity<ApiErrorResponse> response = handler.handleOtpInvalidException(
                new OtpInvalidException("Invalid otp"),
                exchange
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("OTP_INVALID");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid OTP");
        assertThat(response.getBody().getRequestId()).isEqualTo("req-789");
        assertThat(response.getBody().getPath()).isEqualTo("/auth/validate-otp/1234");
    }

    @Test
    void handlesUnexpectedExceptionsWithInternalServerErrorPayload() {
        OtpExceptionHandler handler = new OtpExceptionHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/pieces/customer1").build()
        );
        exchange.getAttributes().put(CorrelationIdWebFilter.REQUEST_ID_ATTRIBUTE, "req-900");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(
                new RuntimeException("boom"),
                exchange
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).doesNotContain("boom");
        assertThat(response.getBody().getRequestId()).isEqualTo("req-900");
        assertThat(response.getBody().getPath()).isEqualTo("/pieces/customer1");
    }

    @Test
    void handlesOtpLockedExceptionWithLockedStatus() {
        OtpExceptionHandler handler = new OtpExceptionHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/validate-otp/1234").build()
        );
        exchange.getAttributes().put(CorrelationIdWebFilter.REQUEST_ID_ATTRIBUTE, "req-lock-001");

        ResponseEntity<ApiErrorResponse> response = handler.handleOtpLockedException(
                new OtpLockedException("Account is locked due to too many failed attempts"),
                exchange
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("OTP_LOCKED");
        assertThat(response.getBody().getMessage()).isEqualTo("OTP verification is locked");
        assertThat(response.getBody().getRequestId()).isEqualTo("req-lock-001");
    }

    @Test
    void mapsResponseStatusExceptionWithoutExposingUnexpectedDetails() {
        OtpExceptionHandler handler = new OtpExceptionHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/seller/seller-2/deactivate").build()
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "The authenticated principal does not own this resource"),
                exchange
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("ACCESS_DENIED");
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");
    }

    @Test
    void mapsMissingResourceToSafeNotFoundContract() {
        OtpExceptionHandler handler = new OtpExceptionHandler();
        MockServerWebExchange exchange = exchange("/pieces/missing");

        ResponseEntity<ApiErrorResponse> response = handler.handleEntityException(
                new EntityNotFoundException("database details"), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void mapsAuthenticationFailureToUnauthorizedWithoutTokenDetails() {
        OtpExceptionHandler handler = new OtpExceptionHandler();

        ResponseEntity<ApiErrorResponse> response = handler.handleGCPTokenException(
                new InvalidGCPToken("provider token payload"), exchange("/auth/google"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getCode()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(response.getBody().getMessage()).doesNotContain("provider token payload");
    }

    @Test
    void mapsStateConflictToConflictContract() {
        OtpExceptionHandler handler = new OtpExceptionHandler();

        ResponseEntity<ApiErrorResponse> response = handler.handleConflictException(
                new ConflictException("duplicate idempotency key"), exchange("/wallet/top-up"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo("Request conflicts with the current state");
    }

    @Test
    void mapsInsufficientBalanceToConflictContract() {
        OtpExceptionHandler handler = new OtpExceptionHandler();

        ResponseEntity<ApiErrorResponse> response = handler.handleInsufficientBalanceException(
                new InsufficientBalanceException("database details"), exchange("/seller/wallet/deduct"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(response.getBody().getMessage()).isEqualTo("Insufficient balance");
    }

    @Test
    void mapsFileFailureToBadRequestWithoutInternalDetails() {
        OtpExceptionHandler handler = new OtpExceptionHandler();

        ResponseEntity<ApiErrorResponse> response = handler.handleFileException(
                new FileUploadException("storage provider response"), exchange("/files"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_FILE_UPLOAD");
        assertThat(response.getBody().getMessage()).doesNotContain("storage provider response");
    }

    @Test
    void mapsPersistenceDuplicateToConflictContract() {
        OtpExceptionHandler handler = new OtpExceptionHandler();

        ResponseEntity<ApiErrorResponse> response = handler.handlePersistenceConflict(
                new DuplicateKeyException("database constraint details"), exchange("/auth/send-otp-email"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
        assertThat(response.getBody().getMessage()).doesNotContain("database constraint details");
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}
