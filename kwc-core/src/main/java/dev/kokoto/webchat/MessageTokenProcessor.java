package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * MessageTokenProcessor는 여러 저수준 객체를 조합해 하나의 KWC 기능 흐름을 수행하는 서비스/관리 계층이다.
 * MessageTokenProcessor is a service/manager layer coordinating lower-level objects into one KWC feature flow.
 *
 * 상태 변경 순서와 실패 시 rollback/재시도 의미가 호출자에게 예측 가능하도록 side effect를 한곳에서 조정한다.
 * Coordinate side effects so mutation order and rollback/retry behavior remain predictable to callers.
 */
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Replaces administrator-configured, colon-delimited message control tokens.
 * Control actions are fixed; aliases are configurable so servers can localize
 * the input vocabulary without putting escape sequences such as \\n in chat.
 */
public final class MessageTokenProcessor {
    private MessageTokenProcessor() {}

    // Private-use markers are used only in transient game-render strings. They let
    // configured newline tokens survive the existing single-line sanitizers without
    // changing how real CR/LF characters are flattened. These markers are never
    // stored in chat/DM/group history.
    private static final String GAME_NEWLINE_MARKER = "\uE10A\uE10B";

    public static String apply(String input, MessageTokenConfig config) {
        return applyInternal(input, config, false);
    }

    public static String applyForGameDisplay(String input, MessageTokenConfig config) {
        return applyInternal(input, config, true);
    }

    public static String restoreGameDisplayBreaks(String input) {
        return String.valueOf(input == null ? "" : input).replace(GAME_NEWLINE_MARKER, "\n");
    }

    /**
     * Converts the transient game-render marker into explicit Minecraft output lines.
     * Ordinary CR/LF is flattened first, preserving the legacy one-line policy; only
     * configured newline/blank-line tokens create additional returned lines.
     */
    public static List<String> splitGameDisplayLines(String input) {
        String text = String.valueOf(input == null ? "" : input)
                .replace('\r', ' ')
                .replace('\n', ' ');
        return List.of(text.split(Pattern.quote(GAME_NEWLINE_MARKER), -1));
    }

    private static String applyInternal(String input, MessageTokenConfig config, boolean protectGameBreaks) {
        String text = String.valueOf(input == null ? "" : input);
        if (text.isEmpty() || config == null || !config.messageTokensEnabled()) return text;

        Map<String, String> replacements = new LinkedHashMap<>();
        String newline = protectGameBreaks ? GAME_NEWLINE_MARKER : "\n";
        putAliases(replacements, config.messageTokenNewlineAliases(), newline);
        putAliases(replacements, config.messageTokenBlankLineAliases(), newline + newline);
        putAliases(replacements, config.messageTokenTabAliases(), " ".repeat(Math.max(1, Math.min(16, config.messageTokenTabSpaces()))));
        if (config.messageTokenCustomReplacements() != null) {
            for (Map.Entry<String, String> entry : config.messageTokenCustomReplacements().entrySet()) {
                String alias = normalizeAlias(entry.getKey());
                String replacement = sanitizeLiteralReplacement(entry.getValue());
                if (!alias.isBlank() && !replacement.isEmpty()) replacements.putIfAbsent(alias, replacement);
            }
        }
        if (replacements.isEmpty()) return text;

        String alternation = replacements.keySet().stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        if (alternation.isEmpty()) return text;
        Pattern tokenPattern = Pattern.compile(":(" + alternation + "):", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        int max = Math.max(0, config.messageTokensMaxReplacements());
        int replaced = 0;
        Matcher matcher = tokenPattern.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String replacement = replacements.get(normalizeAlias(matcher.group(1)));
            if (replacement == null || (max > 0 && replaced >= max)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            replaced++;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String normalizeAlias(String raw) {
        String alias = String.valueOf(raw == null ? "" : raw).trim();
        while (alias.startsWith(":")) alias = alias.substring(1).trim();
        while (alias.endsWith(":")) alias = alias.substring(0, alias.length() - 1).trim();
        if (alias.length() > 80 || alias.indexOf(':') >= 0 || alias.indexOf('\n') >= 0 || alias.indexOf('\r') >= 0) return "";
        return alias.toLowerCase(Locale.ROOT);
    }

    private static void putAliases(Map<String, String> out, List<String> aliases, String replacement) {
        if (aliases == null) return;
        for (String raw : aliases) {
            String alias = normalizeAlias(raw);
            if (!alias.isBlank()) out.putIfAbsent(alias, replacement);
        }
    }

    private static String sanitizeLiteralReplacement(String raw) {
        String text = String.valueOf(raw == null ? "" : raw);
        return text.replaceAll("[\\p{Cntrl}]", "");
    }
}
