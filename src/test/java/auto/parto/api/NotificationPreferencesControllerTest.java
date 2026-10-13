package auto.parto.api;

import auto.parto.dto.NotificationPreferencesDto;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.service.NotificationPreferencesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferencesControllerTest {

    @Mock
    private NotificationPreferencesService preferencesService;

    @Test
    void getPreferences_returnsStoredPreferences() {
        NotificationPreferencesDto dto = NotificationPreferencesDto.builder()
                .userId("user-123")
                .emailEnabled(true)
                .pushEnabled(false)
                .build();

        when(preferencesService.getPreferences("user-123", "user-123")).thenReturn(Mono.just(dto));

        NotificationPreferencesController controller = new NotificationPreferencesController(preferencesService);

        StepVerifier.create(controller.getPreferences(new MockAuth("user-123")))
                .expectNextMatches(response -> 
                    response.getStatusCode() == HttpStatus.OK &&
                    response.getBody().isEmailEnabled() &&
                    !response.getBody().isPushEnabled()
                )
                .verifyComplete();
    }

    @Test
    void updatePreferences_returnsUpdatedPreferences() {
        NotificationPreferencesDto input = NotificationPreferencesDto.builder()
                .emailEnabled(false)
                .pushEnabled(true)
                .build();

        when(preferencesService.updatePreferences(anyString(), anyString(), any(NotificationPreferencesDto.class)))
                .thenReturn(Mono.just(input));

        NotificationPreferencesController controller = new NotificationPreferencesController(preferencesService);

        StepVerifier.create(controller.updatePreferences(new MockAuth("user-123"), input))
                .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK)
                .verifyComplete();
    }

    @Test
    void isChannelEnabled_returnsTrue_whenEnabled() {
        when(preferencesService.isChannelEnabled("user-123", "user-123", "email")).thenReturn(Mono.just(true));

        NotificationPreferencesController controller = new NotificationPreferencesController(preferencesService);

        StepVerifier.create(controller.isChannelEnabled(new MockAuth("user-123"), "email"))
                .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK && response.getBody())
                .verifyComplete();
    }

    @Test
    void isChannelEnabled_returnsFalse_whenDisabled() {
        when(preferencesService.isChannelEnabled("user-123", "user-123", "push")).thenReturn(Mono.just(false));

        NotificationPreferencesController controller = new NotificationPreferencesController(preferencesService);

        StepVerifier.create(controller.isChannelEnabled(new MockAuth("user-123"), "push"))
                .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK && !response.getBody())
                .verifyComplete();
    }

    @Test
    void isNotificationTypeEnabled_returnsTrue_whenEnabled() {
        when(preferencesService.isNotificationTypeEnabled("user-123", "user-123", "pricealert"))
                .thenReturn(Mono.just(true));

        NotificationPreferencesController controller = new NotificationPreferencesController(preferencesService);

        StepVerifier.create(controller.isNotificationTypeEnabled(new MockAuth("user-123"), "pricealert"))
                .expectNextMatches(response -> response.getStatusCode() == HttpStatus.OK && response.getBody())
                .verifyComplete();
    }

    @Test
    void usesCanonicalPrincipalIdWhenItDiffersFromEmail() {
        CanonicalPrincipal principal = new CanonicalPrincipal("buyer-123", "buyer@test.com", "ROLE_BUYER");
        when(preferencesService.getPreferences("buyer-123", "buyer@test.com"))
                .thenReturn(Mono.just(NotificationPreferencesDto.builder().userId("buyer-123").build()));

        NotificationPreferencesController controller = new NotificationPreferencesController(preferencesService);

        StepVerifier.create(controller.getPreferences(new MockAuth(principal)))
                .expectNextCount(1)
                .verifyComplete();

        verify(preferencesService).getPreferences("buyer-123", "buyer@test.com");
    }

    @Test
    void getPreferences_rejectsNonCanonicalPrincipalWith401() {
        NotificationPreferencesController controller = new NotificationPreferencesController(preferencesService);

        assertThatThrownBy(() -> controller.getPreferences(new MockAuth(new Object())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    static class MockAuth implements Authentication {
        private final Object principal;
        MockAuth(String name) { this(new CanonicalPrincipal(name, name, "ROLE_BUYER")); }
        MockAuth(Object principal) { this.principal = principal; }
        @Override public String getName() { return principal instanceof CanonicalPrincipal canonical ? canonical.getUsername() : principal.toString(); }
        @Override public Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() { return List.of(); }
        @Override public Object getCredentials() { return null; }
        @Override public Object getDetails() { return null; }
        @Override public Object getPrincipal() { return principal; }
        @Override public boolean isAuthenticated() { return true; }
        @Override public void setAuthenticated(boolean b) {}
    }
}
