package dev.kokoto.webchat;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static class Captcha {
        public final String id;
        public final String question;
        private final String answer;
        private final long expiresAt;

        private Captcha(String id, String question, String answer, long expiresAt) {
            this.id = id;
            this.question = question;
            this.answer = answer;
            this.expiresAt = expiresAt;
        }

        public boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final Map<String, Captcha> captchas = new ConcurrentHashMap<>();
    private final Map<String, Long> passes = new ConcurrentHashMap<>();
    private final Map<String, Long> ipPasses = new ConcurrentHashMap<>();

    public Captcha issueMath(int expireSeconds) {
        int a = 1 + RANDOM.nextInt(9);
        int b = 1 + RANDOM.nextInt(9);
        String id = SecurityUtil.randomToken(12);
        Captcha c = new Captcha(id, a + " + " + b + " = ?", String.valueOf(a + b),
                System.currentTimeMillis() + expireSeconds * 1000L);
        captchas.put(id, c);
        return c;
    }

    public boolean verify(String id, String answer) {
        if (id == null || answer == null) return false;
        Captcha c = captchas.remove(id);
        if (c == null || c.expired()) return false;
        return c.answer.equals(answer.trim());
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
        return "math".equalsIgnoreCase(String.valueOf(captchaMode == null ? "" : captchaMode));
    }
}
