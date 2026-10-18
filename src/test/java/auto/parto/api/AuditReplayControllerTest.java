package auto.parto.api;

import auto.parto.audit.AuditPublisher;
import auto.parto.security.CanonicalPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuditReplayControllerTest {

    private final AuditPublisher publisher = mock(AuditPublisher.class);
    private final AuditReplayController controller = new AuditReplayController(publisher);

    @Test
    void replaysOnlyThroughCanonicalAdminPrincipal() {
        UUID eventId = UUID.randomUUID();
        when(publisher.replay(eventId, "admin-1")).thenReturn(Mono.empty());

        controller.replay(eventId, authentication("admin-1", "ROLE_ADMIN")).block();

        verify(publisher).replay(eventId, "admin-1");
    }

    @Test
    void rejectsNonAdminPrincipalBeforeCallingPublisher() {
        assertThatThrownBy(() -> controller.replay(UUID.randomUUID(), authentication("buyer-1", "ROLE_BUYER")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(403);

        verifyNoInteractions(publisher);
    }

    private Authentication authentication(String id, String role) {
        CanonicalPrincipal principal = new CanonicalPrincipal(id, id + "@example.test", role);
        return new UsernamePasswordAuthenticationToken(principal, "token", principal.getAuthorities());
    }
}
