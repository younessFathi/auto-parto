package auto.parto.config;

import auto.parto.dto.ApiErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        Object requestIdValue = exchange.getAttribute(CorrelationIdWebFilter.REQUEST_ID_ATTRIBUTE);
        String requestId = requestIdValue == null ? UUID.randomUUID().toString() : requestIdValue.toString();
        exchange.getResponse().getHeaders().set(CorrelationIdWebFilter.REQUEST_ID_HEADER, requestId);
        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .requestId(requestId)
                .build();
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(response);
        } catch (JsonProcessingException exception) {
            return Mono.error(new IllegalStateException("Unable to serialize API error response", exception));
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
