package auto.parto.exception;

public class TwilioException extends RuntimeException {
    public TwilioException() {}
    public TwilioException(String message) {
        super(message);
    }
    public TwilioException(String message, Throwable cause) {
        super(message, cause);
    }
}
