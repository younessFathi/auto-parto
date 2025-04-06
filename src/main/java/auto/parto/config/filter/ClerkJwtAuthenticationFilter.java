package auto.parto.config.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
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
            return chain.filter(exchange); // No auth → continue (maybe it's a public route)
        }

        String token = authHeader.substring(BEARER.length());

        try {
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) getPublicKey(), null);
            JWTVerifier verifier = JWT.require(algorithm).withIssuer(CLERK_ISSUER).build();
            DecodedJWT jwt = verifier.verify(token);

            String userId = jwt.getSubject();

            // Add user ID to request attributes for downstream use
            exchange.getAttributes().put("userId", userId);
            String email = jwt.getClaim("email").asString();
            String name = jwt.getClaim("name").asString();
            String picture = jwt.getClaim("picture").asString();

            exchange.getAttributes().put("userId", userId);
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
