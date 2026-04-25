package auto.parto.advicer;

import auto.parto.config.CorrelationIdWebFilter;
import auto.parto.dto.ApiErrorResponse;
import auto.parto.exception.OtpInvalidException;
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
}
