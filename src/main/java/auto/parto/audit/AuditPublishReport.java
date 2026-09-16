package auto.parto.audit;

public record AuditPublishReport(long published, long retried, long exhausted) {
}
