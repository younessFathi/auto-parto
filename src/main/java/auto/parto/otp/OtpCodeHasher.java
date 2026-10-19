package auto.parto.otp;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Hashes short-lived OTP codes before they cross a persistence boundary. */
public final class OtpCodeHasher {

    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String hashCode(String otp) {
        return hashCode(otp, RANDOM);
    }

    public static boolean matchesCode(String otp, String encodedHash) {
        if (otp == null || otp.isBlank() || encodedHash == null || encodedHash.isBlank()) {
            return false;
        }
        try {
            String[] parts = encodedHash.split(":", -1);
            if (parts.length != 2) return false;
            byte[] salt = Base64.getUrlDecoder().decode(parts[0]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[1]);
            return MessageDigest.isEqual(expected, derive(otp.toCharArray(), salt));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String hashCode(String otp, SecureRandom random) {
        requireOtp(otp);
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return encode(salt, derive(otp.toCharArray(), salt));
    }

    private static String encode(byte[] salt, byte[] hash) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(salt) + ":" + encoder.encodeToString(hash);
    }

    private static byte[] derive(char[] otp, byte[] salt) {
        try {
            PBEKeySpec specification = new PBEKeySpec(otp, salt, ITERATIONS, KEY_LENGTH);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification)
                    .getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("OTP hashing is unavailable", exception);
        }
    }

    private static void requireOtp(String otp) {
        if (otp == null || otp.isBlank()) throw new IllegalArgumentException("otp is required");
    }
}
