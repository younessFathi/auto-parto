package auto.parto.advicer;

import auto.parto.config.CorrelationIdWebFilter;
import auto.parto.dto.ApiErrorResponse;
import auto.parto.exception.OtpInvalidException;
import auto.parto.exception.OtpLockedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

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
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid otp");
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
        assertThat(response.getBody().getMessage()).isEqualTo("Account is locked due to too many failed attempts");
        assertThat(response.getBody().getRequestId()).isEqualTo("req-lock-001");
    }
}
