package auto.parto.exception;

public class OtpExpiredException extends RuntimeException{
    public OtpExpiredException() {}
    public OtpExpiredException(String message) {
        super(message);
    }
}
