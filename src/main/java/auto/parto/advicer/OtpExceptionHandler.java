package auto.parto.advicer;

import auto.parto.exception.*;
import auto.parto.utils.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class OtpExceptionHandler {

    @ExceptionHandler(OtpInvalidException.class)
    public ResponseEntity<Object> handleOtpInvalidException(OtpInvalidException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<Object> handleOtpExpiredException(OtpExpiredException ex) {
        return buildErrorResponse(HttpStatus.GONE, ex.getMessage());
    }

    @ExceptionHandler(OtpTooManyRequestsException.class)
    public ResponseEntity<Object> handleOtpTooManyRequestsException(OtpTooManyRequestsException ex) {
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(TwilioException.class)
    public ResponseEntity<Object> handleTwilioException(TwilioException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, Constants.Exception.AN_UNEXPECTED_ERROR_OCCURRED);
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<Object> handleTwilioException(FileUploadException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Object> handleValidationException(WebExchangeBindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(errors);
    }

    // Generic exception handler (optional)

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put(Constants.Exception.TIMESTAMP, LocalDateTime.now());
        errorDetails.put(Constants.Exception.STATUS, status.value());
        errorDetails.put(Constants.Exception.ERROR, status.getReasonPhrase());
        errorDetails.put(Constants.Exception.MESSAGE, message);

        return new ResponseEntity<>(errorDetails, status);
    }
}
