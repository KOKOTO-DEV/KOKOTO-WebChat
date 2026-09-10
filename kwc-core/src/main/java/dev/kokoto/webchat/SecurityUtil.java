package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * SecurityUtil는 인증·입력 검증·접속 제한 중 하나를 담당하는 보안 경계 코드다.
 * SecurityUtil is security-boundary code responsible for authentication, input validation, or access limiting.
 *
 * 화면에서 버튼을 숨기는 것은 권한 검사가 아니므로, 모든 민감한 작업은 서버에서 UUID/세션/권한을 다시 검증해야 한다.
 * Hiding a button is not authorization; every sensitive action must revalidate UUID/session/permission on the server.
 */
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecurityUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecurityUtil() {}

    public static String randomToken(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static String randomNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public static String hashPassword(char[] password) {
        try {
            int iterations = 120_000;
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] hash = pbkdf2(password, salt, iterations, 32);
            return "pbkdf2$" + iterations + "$" + b64(salt) + "$" + b64(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash password", ex);
        }
    }

    public static boolean verifyPassword(char[] password, String stored) {
        if (stored == null || stored.isBlank()) return false;
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ex) {
            return false;
        }
    }

    public static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(data.length * 2);
            for (byte b : data) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }

    private static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
