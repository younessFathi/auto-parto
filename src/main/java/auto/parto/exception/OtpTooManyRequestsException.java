package auto.parto.exception;

public class OtpTooManyRequestsException extends RuntimeException {
    public OtpTooManyRequestsException() {}
    public OtpTooManyRequestsException(String message) {
        super(message);
    }
}
