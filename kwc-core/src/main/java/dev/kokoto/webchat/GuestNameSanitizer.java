package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * GuestNameSanitizer는 인증·입력 검증·접속 제한 중 하나를 담당하는 보안 경계 코드다.
 * GuestNameSanitizer is security-boundary code responsible for authentication, input validation, or access limiting.
 *
 * 화면에서 버튼을 숨기는 것은 권한 검사가 아니므로, 모든 민감한 작업은 서버에서 UUID/세션/권한을 다시 검증해야 한다.
 * Hiding a button is not authorization; every sensitive action must revalidate UUID/session/permission on the server.
 */
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Loader-neutral guest-name sanitization.
 *
 * <p>Custom guest names accept Unicode letters and numbers, space separators,
 * underscore, and hyphen. Unicode space-separator characters are normalized to
 * one ASCII space; tabs, newlines, control characters, symbols, and other
 * punctuation are removed.</p>
 */
public final class GuestNameSanitizer {
    private static final Pattern LEGACY_HEX_SEQUENCE = Pattern.compile("(?i)[§&]x(?:[§&][0-9a-f]){6}");
    private static final Pattern AMPERSAND_HEX = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern LEGACY_FORMAT_CODE = Pattern.compile("(?i)[§&][0-9a-fk-orx]");
    private GuestNameSanitizer() {
    }

    public static String sanitizeCustom(String name, int maxCodePoints) {
        return sanitizeAllowed(name, maxCodePoints, true);
    }

    public static String sanitizePrefix(String prefix, String fallback) {
        String sanitized = sanitizeAllowed(prefix, 0, true);
        if (!sanitized.isBlank()) return sanitized;
        String safeFallback = sanitizeAllowed(fallback, 0, true);
        return safeFallback.isBlank() ? "Guest-" : safeFallback;
    }

    public static String sanitizeGenerated(String name, String prefix) {
        if (name == null) return "";
        String safePrefix = sanitizePrefix(prefix, "Guest-");
        String sanitized = sanitizeAllowed(name, 0, true);
        if (!sanitized.startsWith(safePrefix)) return "";
        String suffix = sanitized.substring(safePrefix.length());
        if (!suffix.matches("\\d{4,8}")) return "";
        return safePrefix + suffix;
    }

    /**
     * Canonical key used only for guest/player-name impersonation checks.
     *
     * <p>Minecraft legacy/hex color and formatting codes are always stripped here,
     * independently of player-display.strip-colors. This lets a protected display
     * name such as {@code &aPlayer} also protect the visible plain name
     * {@code Player} when colored names are enabled on the web UI.</p>
     */
    public static String spoofComparisonKey(String value) {
        if (value == null || value.isBlank()) return "";

        String plain = LEGACY_HEX_SEQUENCE.matcher(value).replaceAll("");
        plain = AMPERSAND_HEX.matcher(plain).replaceAll("");
        plain = LEGACY_FORMAT_CODE.matcher(plain).replaceAll("");
        plain = Normalizer.normalize(plain, Normalizer.Form.NFC);

        StringBuilder out = new StringBuilder(plain.length());
        boolean previousSpace = false;
        for (int offset = 0; offset < plain.length();) {
            int cp = plain.codePointAt(offset);
            offset += Character.charCount(cp);

            if (Character.getType(cp) == Character.SPACE_SEPARATOR || Character.isWhitespace(cp)) {
                if (out.length() > 0 && !previousSpace) {
                    out.append(' ');
                    previousSpace = true;
                }
                continue;
            }
            if (Character.isISOControl(cp)) continue;
            out.appendCodePoint(cp);
            previousSpace = false;
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') out.setLength(out.length() - 1);
        return out.toString().toLowerCase(Locale.ROOT);
    }

    private static String sanitizeAllowed(String input, int maxCodePoints, boolean collapseSpaces) {
        if (input == null || input.isEmpty()) return "";

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(normalized.length());
        boolean previousSpace = false;

        for (int offset = 0; offset < normalized.length();) {
            int cp = normalized.codePointAt(offset);
            offset += Character.charCount(cp);

            if (Character.isLetterOrDigit(cp) || cp == '_' || cp == '-') {
                out.appendCodePoint(cp);
                previousSpace = false;
                continue;
            }

            if (Character.getType(cp) == Character.SPACE_SEPARATOR) {
                if (out.length() > 0 && (!collapseSpaces || !previousSpace)) {
                    out.append(' ');
                    previousSpace = true;
                }
            }
        }

        while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
            out.setLength(out.length() - 1);
        }

        if (maxCodePoints > 0) {
            int count = out.codePointCount(0, out.length());
            if (count > maxCodePoints) {
                int end = out.offsetByCodePoints(0, maxCodePoints);
                out.setLength(end);
                while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                    out.setLength(out.length() - 1);
                }
            }
        }
        return out.toString();
    }
}
