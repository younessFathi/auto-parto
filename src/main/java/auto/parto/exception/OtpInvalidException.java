package auto.parto.exception;

public class OtpInvalidException extends RuntimeException {
    public OtpInvalidException() {}
    public OtpInvalidException(String message) {
        super(message);
    }
}
