package auto.parto.service;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SecretsManagerService {

    @org.springframework.beans.factory.annotation.Value("${jwt.secretKey:}")
    private String baseSecretKey;

    private final ConcurrentHashMap<String, String> keyCache = new ConcurrentHashMap<>();

    public String getKeyForVersion(String keyVersion) {
        if (keyVersion == null || keyVersion.isBlank()) {
            return baseSecretKey;
        }
        return keyCache.computeIfAbsent(keyVersion, this::deriveKey);
    }

    private String deriveKey(String keyVersion) {
        try {
            String combined = baseSecretKey + ":" + keyVersion;
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hashed = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = new byte[64];
            System.arraycopy(hashed, 0, signingKey, 0, 64);
            return Base64.getEncoder().encodeToString(signingKey);
        } catch (Exception e) {
            log.error("Failed to derive legacy key material");
            return baseSecretKey;
        }
    }

    public String rotateKey(String currentVersion) {
        int newVersion = (currentVersion == null ? 0 : Integer.parseInt(currentVersion)) + 1;
        String newVersionStr = String.valueOf(newVersion);
        log.info("Rotating JWT key to version: {}", newVersionStr);
        return newVersionStr;
    }
}
