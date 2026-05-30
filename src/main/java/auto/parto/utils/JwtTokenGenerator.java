package auto.parto.utils;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.JwtKeyProvider;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.function.Function;

import static io.jsonwebtoken.Jwts.*;

@Component
@Slf4j
public class JwtTokenGenerator {
    public static final String ROLE_PREFIX = "ROLE_";
    @Value("${jwt.secretKey}")
    private String secretKey;
    @Value("${jwt.expiration}")
    private long expirationTime;
    private static final String AUTHORITIES_KEY = "roles";

    @PostConstruct
    public void validateConfiguration() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                "JWT secret key is not configured. Set JWT_SECRET environment variable to a Base64-encoded key of at least 256 bits."
            );
        }
        if (secretKey.contains("${")) {
            throw new IllegalStateException(
                "JWT secret key is not configured. Set JWT_SECRET environment variable to a Base64-encoded key of at least 256 bits. " +
                "Unresolved placeholder: " + secretKey
            );
        }
        try {
            byte[] decodedKey = Base64.getDecoder().decode(secretKey);
            if (decodedKey.length < 32) {
                throw new IllegalStateException(
                    "JWT secret key must be at least 256 bits (32 bytes) after Base64 decoding. " +
                    "Current key is only " + (decodedKey.length * 8) + " bits."
                );
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "JWT secret key is not a valid Base64-encoded string. " +
                "Set JWT_SECRET environment variable to a Base64-encoded key of at least 256 bits."
            );
        }
    }

    public <T extends JwtKeyProvider> Mono<TokenDto<T>> encode(String value , T user , CustomerType type) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        String token = Jwts.builder()
                            .subject(value)
                            .claim(AUTHORITIES_KEY , ROLE_PREFIX + type.name())
                            .signWith(getSigningKey())
                            .issuedAt(now)
                            .expiration(expiryDate)
                            .compact();
        return Mono.just(
                TokenDto.<T>builder()
                        .token(token)
                        .user(user)
                        .build()
        );
    }

    private SecretKey getSigningKey() {
        byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        return Keys.hmacShaKeyFor(decodedKey);
    }

    public Mono<TokenDto> encode(String value, String role) {
        String token = generateToken(value, role);
        return Mono.just(
                TokenDto.builder()
                        .token(token)
                        .build()
        );
    }

    public String generateToken(String value, String role) {
        byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        Key key = Keys.hmacShaKeyFor(decodedKey);
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiryDate = new Date(nowMillis + expirationTime);
        String authorities = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
        String token = Jwts.builder()
                .subject(value)
                .issuedAt(now)
                .expiration(expiryDate)
                .claim(AUTHORITIES_KEY, authorities)
                .signWith(key)
                .compact();
        return token;
    }

    public static String keyGenerator(String phoneNumber) {
        KeyGenerator keyGen = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hashedBytes = digest.digest(phoneNumber.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = new byte[64];
            System.arraycopy(hashedBytes, 0, signingKey, 0, 64);
            return Base64.getEncoder().encodeToString(signingKey);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public Boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().after(new java.util.Date());
        } catch (Exception e) {
            log.info("exception {} " , e.getMessage());
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        SecretKey key = Keys.hmacShaKeyFor(decodedKey);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Authentication getAuthentication(String token) {
        Claims claims = extractAllClaims(token);

        Object authoritiesClaim = claims.get(AUTHORITIES_KEY);

        Collection<? extends GrantedAuthority> authorities = authoritiesClaim == null
                ? AuthorityUtils.NO_AUTHORITIES
                : AuthorityUtils.commaSeparatedStringToAuthorityList(authoritiesClaim.toString());

        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }
}
