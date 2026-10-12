package auto.parto.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalSecurityTest {

    private static final Authentication BUYER = authentication("buyer-1", "ROLE_BUYER");

    @Test
    void requirePrincipalReturnsCanonicalPrincipal() {
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(BUYER);

        assertThat(principal.getId()).isEqualTo("buyer-1");
    }

    @Test
    void requirePrincipalRejectsMissingAuthentication() {
        assertThatThrownBy(() -> CanonicalSecurity.requirePrincipal(null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void requirePrincipalRejectsNonCanonicalPrincipal() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("buyer-1", "token");

        assertThatThrownBy(() -> CanonicalSecurity.requirePrincipal(authentication))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void requireOwnerAcceptsMatchingIdAndRole() {
        assertThat(CanonicalSecurity.requireOwner(BUYER, "buyer-1", "ROLE_BUYER").getId())
                .isEqualTo("buyer-1");
    }

    @Test
    void requireOwnerRejectsWrongRole() {
        assertThatThrownBy(() -> CanonicalSecurity.requireOwner(BUYER, "buyer-1", "ROLE_SELLER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void requireOwnerRejectsDifferentId() {
        assertThatThrownBy(() -> CanonicalSecurity.requireOwner(BUYER, "buyer-2", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void requireClaimedOwnerAllowsOmittedClaimAndRejectsDifferentClaim() {
        assertThat(CanonicalSecurity.requireClaimedOwner(BUYER, null, "ROLE_BUYER").getId())
                .isEqualTo("buyer-1");

        assertThatThrownBy(() -> CanonicalSecurity.requireClaimedOwner(BUYER, "buyer-2", "ROLE_BUYER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(403);
    }

    private static Authentication authentication(String id, String role) {
        CanonicalPrincipal principal = new CanonicalPrincipal(id, id + "@test.example", role);
        return new UsernamePasswordAuthenticationToken(principal, "token", principal.getAuthorities());
    }
}
