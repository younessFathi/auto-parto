package auto.parto.audit;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AuditStore {

    Mono<AuditAppendResult> append(AuditEvent event);

    Mono<Boolean> contains(UUID eventId);

    default Mono<Void> readiness() {
        return Mono.empty();
    }
}
