package auto.parto.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The single application principal created after a signed OTP JWT is validated.
 */
public final class CanonicalPrincipal implements UserDetails {

    private final String id;
    private final String email;
    private final String role;
    private final List<GrantedAuthority> authorities;

    public CanonicalPrincipal(String id, String email, String role) {
        this.id = requireValue(id, "id");
        this.email = requireValue(email, "email");
        this.role = requireValue(role, "role");
        this.authorities = List.of(new SimpleGrantedAuthority(role));
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private static String requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Canonical principal " + field + " is required");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CanonicalPrincipal that)) {
            return false;
        }
        return id.equals(that.id) && email.equals(that.email) && role.equals(that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email, role);
    }
}
