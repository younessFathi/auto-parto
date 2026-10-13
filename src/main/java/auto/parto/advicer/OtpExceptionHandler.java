package auto.parto.advicer;

import auto.parto.config.CorrelationIdWebFilter;
import auto.parto.dto.ApiErrorResponse;
import auto.parto.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.ConstraintViolationException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class OtpExceptionHandler {

    @ExceptionHandler(OtpInvalidException.class)
    public ResponseEntity<ApiErrorResponse> handleOtpInvalidException(OtpInvalidException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleOtpExpiredException(OtpExpiredException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.GONE, ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(OtpTooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleOtpTooManyRequestsException(OtpTooManyRequestsException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(OtpLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleOtpLockedException(OtpLockedException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.LOCKED, ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ApiErrorResponse> handleFileException(FileUploadException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityException(EntityNotFoundException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(InvalidGCPToken.class)
    public ResponseEntity<ApiErrorResponse> handleGCPTokenException(InvalidGCPToken ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientBalanceException(InsufficientBalanceException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(WebExchangeBindException ex, ServerWebExchange exchange) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation failed", exchange, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException ex,
                                                                               ServerWebExchange exchange) {
        Map<String, String> details = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (first, second) -> first
                ));
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation failed", exchange, details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                           ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException ex,
                                                                            ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus safeStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String message = safeStatus.is4xxClientError() ? ex.getReason() : "An unexpected error occurred";
        return buildErrorResponse(safeStatus, message, exchange, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex,
                                                                          ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access denied", exchange, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex,
                                                                      ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", exchange, null);
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(HttpStatus status,
                                                                String message,
                                                                ServerWebExchange exchange,
                                                                Map<String, String> details) {
        Object requestIdValue = exchange.getAttribute(CorrelationIdWebFilter.REQUEST_ID_ATTRIBUTE);
        String requestId = requestIdValue == null ? null : requestIdValue.toString();
        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .requestId(requestId)
                .details(details)
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }
}
