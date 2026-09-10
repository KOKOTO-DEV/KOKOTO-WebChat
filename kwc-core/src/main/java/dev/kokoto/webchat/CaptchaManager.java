package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * CaptchaManager는 인증·입력 검증·접속 제한 중 하나를 담당하는 보안 경계 코드다.
 * CaptchaManager is security-boundary code responsible for authentication, input validation, or access limiting.
 *
 * 화면에서 버튼을 숨기는 것은 권한 검사가 아니므로, 모든 민감한 작업은 서버에서 UUID/세션/권한을 다시 검증해야 한다.
 * Hiding a button is not authorization; every sensitive action must revalidate UUID/session/permission on the server.
 */
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] TEXT_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    public static class Captcha {
        public final String id;
        public final String type;
        public final String question;
        private final String answer;
        private final boolean caseInsensitive;
        private final long expiresAt;

        private Captcha(String id, String type, String question, String answer, boolean caseInsensitive, long expiresAt) {
            this.id = id;
            this.type = type;
            this.question = question;
            this.answer = answer;
            this.caseInsensitive = caseInsensitive;
            this.expiresAt = expiresAt;
        }

        public boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final Map<String, Captcha> captchas = new ConcurrentHashMap<>();
    private final Map<String, Long> passes = new ConcurrentHashMap<>();
    private final Map<String, Long> ipPasses = new ConcurrentHashMap<>();

    public Captcha issue(String captchaMode, int expireSeconds) {
        return issue(captchaMode, expireSeconds, "normal");
    }

    public Captcha issue(String captchaMode, int expireSeconds, String mathComplexity) {
        String mode = String.valueOf(captchaMode == null ? "" : captchaMode).trim().toLowerCase(Locale.ROOT);
        if ("mixed".equals(mode)) mode = RANDOM.nextBoolean() ? "math" : "text";
        return "text".equals(mode) ? issueText(expireSeconds) : issueMath(expireSeconds, mathComplexity);
    }

    public Captcha issueMath(int expireSeconds) {
        return issueMath(expireSeconds, "normal");
    }

    public Captcha issueMath(int expireSeconds, String complexity) {
        String level = String.valueOf(complexity == null ? "normal" : complexity).trim().toLowerCase(Locale.ROOT);
        if (!"easy".equals(level) && !"hard".equals(level)) level = "normal";
        int a, b, answer;
        String op;
        if ("easy".equals(level)) {
            a = 1 + RANDOM.nextInt(9);
            b = 1 + RANDOM.nextInt(9);
            if (RANDOM.nextBoolean()) { op = "+"; answer = a + b; }
            else { if (b > a) { int t = a; a = b; b = t; } op = "-"; answer = a - b; }
        } else if ("hard".equals(level)) {
            int choice = RANDOM.nextInt(4);
            if (choice == 0) { a = 10 + RANDOM.nextInt(90); b = 10 + RANDOM.nextInt(90); op = "+"; answer = a + b; }
            else if (choice == 1) { a = 10 + RANDOM.nextInt(90); b = 1 + RANDOM.nextInt(a); op = "-"; answer = a - b; }
            else if (choice == 2) { a = 2 + RANDOM.nextInt(11); b = 2 + RANDOM.nextInt(11); op = "×"; answer = a * b; }
            else { b = 2 + RANDOM.nextInt(11); answer = 2 + RANDOM.nextInt(11); a = b * answer; op = "÷"; }
        } else {
            int choice = RANDOM.nextInt(3);
            if (choice == 0) { a = 1 + RANDOM.nextInt(30); b = 1 + RANDOM.nextInt(30); op = "+"; answer = a + b; }
            else if (choice == 1) { a = 1 + RANDOM.nextInt(30); b = 1 + RANDOM.nextInt(a); op = "-"; answer = a - b; }
            else { a = 2 + RANDOM.nextInt(8); b = 2 + RANDOM.nextInt(8); op = "×"; answer = a * b; }
        }
        String id = SecurityUtil.randomToken(12);
        Captcha c = new Captcha(id, "math", a + " " + op + " " + b + " = ?", String.valueOf(answer), false,
                System.currentTimeMillis() + expireSeconds * 1000L);
        captchas.put(id, c);
        return c;
    }

    public Captcha issueText(int expireSeconds) {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) code.append(TEXT_ALPHABET[RANDOM.nextInt(TEXT_ALPHABET.length)]);
        String id = SecurityUtil.randomToken(12);
        Captcha c = new Captcha(id, "text", code.toString(), code.toString(), true,
                System.currentTimeMillis() + expireSeconds * 1000L);
        captchas.put(id, c);
        return c;
    }

    public boolean verify(String id, String answer) {
        if (id == null || answer == null) return false;
        Captcha c = captchas.remove(id);
        if (c == null || c.expired()) return false;
        String actual = answer.trim();
        return c.caseInsensitive ? c.answer.equalsIgnoreCase(actual) : c.answer.equals(actual);
    }

    public String issuePass(int validMinutes) {
        cleanupPasses();
        String token = SecurityUtil.randomToken(24);
        long expiresAt = System.currentTimeMillis() + Math.max(1, validMinutes) * 60_000L;
        passes.put(SecurityUtil.sha256Hex(token), expiresAt);
        return token;
    }

    public boolean verifyPass(String token) {
        if (token == null || token.isBlank()) return false;
        cleanupPasses();
        Long expiresAt = passes.get(SecurityUtil.sha256Hex(token));
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    public void issueIpPass(String ip, int validMinutes) {
        if (ip == null || ip.isBlank()) return;
        cleanupPasses();
        long expiresAt = System.currentTimeMillis() + Math.max(1, validMinutes) * 60_000L;
        ipPasses.put(SecurityUtil.sha256Hex(ip), expiresAt);
    }

    public boolean verifyIpPass(String ip) {
        if (ip == null || ip.isBlank()) return false;
        cleanupPasses();
        Long expiresAt = ipPasses.get(SecurityUtil.sha256Hex(ip));
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    private void cleanupPasses() {
        long now = System.currentTimeMillis();
        passes.entrySet().removeIf(e -> e.getValue() <= now);
        ipPasses.entrySet().removeIf(e -> e.getValue() <= now);
    }

    public boolean enabled(String captchaMode) {
        String mode = String.valueOf(captchaMode == null ? "" : captchaMode).trim().toLowerCase(Locale.ROOT);
        return "math".equals(mode) || "text".equals(mode) || "mixed".equals(mode);
    }
}
