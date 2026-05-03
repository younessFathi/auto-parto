package auto.parto.config.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class ClerkJwtAuthenticationFilter implements WebFilter {
    private static final String BEARER = "Bearer ";
    private static final String CLERK_ISSUER = "https://clerk.YOUR_DOMAIN.clerk.accounts";
    private static final String PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER)) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(BEARER.length());

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();

            exchange.getAttributes().put("userId", userId);
            String email = claims.get("email", String.class);
            String name = claims.get("name", String.class);
            String picture = claims.get("picture", String.class);

            exchange.getAttributes().put("email", email);
            exchange.getAttributes().put("name", name);
            exchange.getAttributes().put("picture", picture);

            return chain.filter(exchange);

        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private PublicKey getPublicKey() throws Exception {
        String clean = ClerkJwtAuthenticationFilter.PUBLIC_KEY
                                .replace("-----BEGIN PUBLIC KEY-----", "")
                                .replace("-----END PUBLIC KEY-----", "")
                                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(clean);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(new X509EncodedKeySpec(encoded));
    }
}
