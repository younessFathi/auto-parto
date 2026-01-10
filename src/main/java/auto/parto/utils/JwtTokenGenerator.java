package auto.parto.utils;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.JwtKeyProvider;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
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
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.function.Function;

@Component
@Slf4j
public class JwtTokenGenerator {
    public static final String ROLE_PREFIX = "ROLE_";
    @Value("${jwt.secretKey}")
    private String secretKey;
    @Value("${jwt.expiration}")
    private long expirationTime;
    private static final String AUTHORITIES_KEY = "roles";

    public <T extends JwtKeyProvider> Mono<TokenDto<T>> encode(String value , T user , CustomerType type) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        String token = Jwts.builder()
                            .setSubject(value)
                            .claim(AUTHORITIES_KEY , ROLE_PREFIX + type.name())
                            .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                            .setIssuedAt(now)
                            .setExpiration(expiryDate)
                            .compact();
        return Mono.just(
                TokenDto.<T>builder()
                        .token(token)
                        .user(user)
                        .build()
        );
    }

    private Key getSigningKey() {
        byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        return Keys.hmacShaKeyFor(decodedKey); // generates HMAC-SHA512 key
    }

    public Mono<TokenDto> encode(String value) {
        String token = generateToken(value);
        return Mono.just(
                TokenDto.builder()
                        .token(token)
                        .build()
        );
    }

    public String generateToken(String value) {
        byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        Key key = new SecretKeySpec(decodedKey, 0, decodedKey.length, "HmacSHA512");
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiryDate = new Date(nowMillis + expirationTime); // one hour
        String token = Jwts.builder()
                .setSubject(value)                // Subject (user identifier)
                .setIssuedAt(now)                     // Issue time
                .setExpiration(expiryDate)                   // Expiration time
                .claim("role", "ADMIN")               // Custom claim
                .signWith(key, SignatureAlgorithm.HS512) // Sign with secret key
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
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(30)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration().after(new java.util.Date());
        } catch (Exception e) {
            log.info("exception {} " , e.getMessage());
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
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
