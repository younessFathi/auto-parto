package auto.parto.exception;

public class OtpLockedException extends RuntimeException {
    public OtpLockedException() {}
    public OtpLockedException(String message) {
        super(message);
    }
}