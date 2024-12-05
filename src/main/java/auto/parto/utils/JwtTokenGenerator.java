package auto.parto.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class JwtTokenGenerator {
    @Value("${jwt.secretKey}")
    private String secretKey;
    @Value("${jwt.expiration}")
    private long expirationTime;

    public Mono<String> generate(String phoneNumber , String key) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        return Mono.just(
                    Jwts.builder()
                    .setSubject(phoneNumber)
                    .signWith(new SecretKeySpec(key.getBytes(), SignatureAlgorithm.HS256.getJcaName())
                            ,SignatureAlgorithm.HS512)
                            .setIssuedAt(now)
                            .setExpiration(expiryDate)
                    .compact()
        );
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
}
