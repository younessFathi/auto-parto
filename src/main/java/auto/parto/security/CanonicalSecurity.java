package auto.parto.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

public final class CanonicalSecurity {

    private CanonicalSecurity() {
    }

    public static CanonicalPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CanonicalPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Canonical authentication is required");
        }
        return principal;
    }

    public static CanonicalPrincipal requireOwner(
            Authentication authentication,
            String requestedId,
            String requiredRole) {
        CanonicalPrincipal principal = requirePrincipal(authentication);
        if (requiredRole != null && !requiredRole.equals(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Required role is missing");
        }
        if (requestedId == null || !principal.getId().equals(requestedId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The authenticated principal does not own this resource");
        }
        return principal;
    }

    public static CanonicalPrincipal requireClaimedOwner(
            Authentication authentication,
            String claimedId,
            String requiredRole) {
        CanonicalPrincipal principal = requirePrincipal(authentication);
        if (requiredRole != null && !requiredRole.equals(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Required role is missing");
        }
        if (claimedId != null && !claimedId.isBlank() && !principal.getId().equals(claimedId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The authenticated principal does not own this resource");
        }
        return principal;
    }
}
