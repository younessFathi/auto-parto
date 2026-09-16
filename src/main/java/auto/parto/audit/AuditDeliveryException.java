package auto.parto.audit;

public final class AuditDeliveryException extends RuntimeException {

    private final boolean retryable;

    public AuditDeliveryException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AuditDeliveryException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
