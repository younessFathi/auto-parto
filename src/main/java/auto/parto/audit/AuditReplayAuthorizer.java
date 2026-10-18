package auto.parto.audit;

import reactor.core.publisher.Mono;

import java.util.UUID;

@FunctionalInterface
public interface AuditReplayAuthorizer {

    Mono<Boolean> authorize(String requester, UUID eventId);
}
