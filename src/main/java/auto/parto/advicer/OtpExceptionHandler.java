package auto.parto.advicer;

import auto.parto.config.CorrelationIdWebFilter;
import auto.parto.dto.ApiErrorResponse;
import auto.parto.exception.ConflictException;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.exception.FileUploadException;
import auto.parto.exception.InsufficientBalanceException;
import auto.parto.exception.InvalidGCPToken;
import auto.parto.exception.OtpExpiredException;
import auto.parto.exception.OtpInvalidException;
import auto.parto.exception.OtpLockedException;
import auto.parto.exception.OtpTooManyRequestsException;
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
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Invalid OTP", exchange, null);
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleOtpExpiredException(OtpExpiredException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.GONE, "OTP_EXPIRED", "OTP has expired", exchange, null);
    }

    @ExceptionHandler(OtpTooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleOtpTooManyRequestsException(OtpTooManyRequestsException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED", "Too many OTP attempts", exchange, null);
    }

    @ExceptionHandler(OtpLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleOtpLockedException(OtpLockedException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.LOCKED, "OTP_LOCKED", "OTP verification is locked", exchange, null);
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ApiErrorResponse> handleFileException(FileUploadException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_FILE_UPLOAD", "Invalid file upload", exchange, null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityException(EntityNotFoundException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", exchange, null);
    }

    @ExceptionHandler(InvalidGCPToken.class)
    public ResponseEntity<ApiErrorResponse> handleGCPTokenException(InvalidGCPToken ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed", exchange, null);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientBalanceException(InsufficientBalanceException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "Insufficient balance", exchange, null);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(WebExchangeBindException ex, ServerWebExchange exchange) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", exchange, errors);
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
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", exchange, details);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflictException(ConflictException ex, ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.CONFLICT, "CONFLICT", "Request conflicts with the current state", exchange, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                           ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request", exchange, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException ex,
                                                                            ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus safeStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        return buildErrorResponse(safeStatus, codeFor(safeStatus), messageFor(safeStatus), exchange, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex,
                                                                          ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", exchange, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex,
                                                                      ServerWebExchange exchange) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", exchange, null);
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(HttpStatus status,
                                                                String code,
                                                                String message,
                                                                ServerWebExchange exchange,
                                                                Map<String, String> details) {
        Object requestIdValue = exchange.getAttribute(CorrelationIdWebFilter.REQUEST_ID_ATTRIBUTE);
        String requestId = requestIdValue == null ? null : requestIdValue.toString();
        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .requestId(requestId)
                .details(details)
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }

    private String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case UNAUTHORIZED -> "AUTHENTICATION_REQUIRED";
            case FORBIDDEN -> "ACCESS_DENIED";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case GONE -> "RESOURCE_GONE";
            case LOCKED -> "RESOURCE_LOCKED";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            default -> "INTERNAL_ERROR";
        };
    }

    private String messageFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Invalid request";
            case UNAUTHORIZED -> "Authentication required";
            case FORBIDDEN -> "Access denied";
            case NOT_FOUND -> "Resource not found";
            case CONFLICT -> "Request conflicts with the current state";
            case GONE -> "Resource is no longer available";
            case LOCKED -> "Resource is locked";
            case TOO_MANY_REQUESTS -> "Too many requests";
            default -> "An unexpected error occurred";
        };
    }
}
