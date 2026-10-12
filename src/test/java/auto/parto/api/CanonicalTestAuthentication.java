package auto.parto.api;

import auto.parto.security.CanonicalPrincipal;
import org.springframework.security.core.Authentication;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

final class CanonicalTestAuthentication implements Authentication {

    private final CanonicalPrincipal principal;

    private CanonicalTestAuthentication(String id, String role) {
        this.principal = new CanonicalPrincipal(id, id + "@test.example", role);
    }

    static CanonicalTestAuthentication buyer(String id) {
        return new CanonicalTestAuthentication(id, "ROLE_BUYER");
    }

    static CanonicalTestAuthentication seller(String id) {
        return new CanonicalTestAuthentication(id, "ROLE_SELLER");
    }

    static CanonicalTestAuthentication admin(String id) {
        return new CanonicalTestAuthentication(id, "ROLE_ADMIN");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return principal.getAuthorities();
    }

    @Override
    public Object getCredentials() {
        return "test-token";
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (!authenticated) {
            throw new IllegalArgumentException("Test authentication cannot be unauthenticated");
        }
    }

    @Override
    public String getName() {
        return principal.getUsername();
    }
}
