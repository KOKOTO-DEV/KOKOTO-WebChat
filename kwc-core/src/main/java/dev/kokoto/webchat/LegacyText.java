package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * LegacyText는 다국어 문자열과 구버전 텍스트 호환을 처리한다.
 * LegacyText handles localized text and compatibility with legacy wording/data.
 *
 * 번역 key는 en-US/ko-KR/ja-JP/zh-CN parity 검증 대상이므로 새 key를 추가할 때 네 언어를 동시에 갱신한다.
 * Localization keys are parity-checked across en-US/ko-KR/ja-JP/zh-CN, so add new keys to all four languages together.
 */
import java.util.regex.Pattern;

/** Minimal platform-neutral Minecraft legacy color-code helpers. */
public final class LegacyText {
    public static final char COLOR_CHAR = '\u00A7';
    public static final String DARK_GRAY = "\u00A78";
    public static final String AQUA = "\u00A7b";
    public static final String LIGHT_PURPLE = "\u00A7d";
    public static final String GRAY = "\u00A77";
    public static final String RESET = "\u00A7r";

    private static final String VALID_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
    private static final Pattern STRIP = Pattern.compile("(?i)" + COLOR_CHAR + "[0-9A-FK-ORX]");

    private LegacyText() {}

    public static String translateAlternateColorCodes(char altColorChar, String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == altColorChar && VALID_CODES.indexOf(chars[i + 1]) >= 0) {
                chars[i] = COLOR_CHAR;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static String stripColor(String text) {
        return text == null ? null : STRIP.matcher(text).replaceAll("");
    }
}
