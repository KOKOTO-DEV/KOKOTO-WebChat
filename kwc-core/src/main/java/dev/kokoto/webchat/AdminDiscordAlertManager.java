package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * AdminDiscordAlertManager는 여러 저수준 객체를 조합해 하나의 KWC 기능 흐름을 수행하는 서비스/관리 계층이다.
 * AdminDiscordAlertManager is a service/manager layer coordinating lower-level objects into one KWC feature flow.
 *
 * 상태 변경 순서와 실패 시 rollback/재시도 의미가 호출자에게 예측 가능하도록 side effect를 한곳에서 조정한다.
 * Coordinate side effects so mutation order and rollback/retry behavior remain predictable to callers.
 */
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Administrator-only keyword alerts delivered through the configured Discord bridge. */
public final class AdminDiscordAlertManager {
    public enum Scope { PUBLIC, RELAY, DM, GROUP }
    private static final long DEDUPE_MILLIS = 120_000L;
    private final WebChatHost host;
    private final Map<String,Long> recent = new ConcurrentHashMap<>();

    public AdminDiscordAlertManager(WebChatHost host) {
        this.host = host;
    }

    public void inspect(ChatMessage message, Scope scope) {
        if (message == null) return;
        inspect(message.id, message.sender, message.source, message.message, scope);
    }

    public void inspect(String id, String sender, String source, String message, Scope scope) {
        ConfigValues c = host.configValues();
        if (c == null || !c.adminDiscordAlertsEnabled || !scopeEnabled(c, scope)) return;
        if (host.discord() == null || message == null || message.isBlank()) return;
        if ("discord".equalsIgnoreCase(String.valueOf(source))) return; // never alert on our Discord inbound path

        List<String> matched = matchedKeywords(c, message);
        if (matched.isEmpty()) return;

        long now = System.currentTimeMillis();
        recent.entrySet().removeIf(e -> now - e.getValue() > DEDUPE_MILLIS);
        String fingerprint = String.valueOf(id == null ? "" : id).trim();
        if (fingerprint.isBlank()) fingerprint = scope + "|" + safe(sender) + "|" + safe(message);
        if (recent.putIfAbsent(fingerprint, now) != null) return;

        String mention = switch (String.valueOf(c.adminDiscordAlertsMention).trim().toLowerCase(Locale.ROOT)) {
            case "here" -> "@here ";
            case "everyone" -> "@everyone ";
            default -> "";
        };
        String scopeLabel = switch (scope) {
            case PUBLIC -> "chat";
            case RELAY -> "relay";
            case DM -> "dm";
            case GROUP -> "group";
        };
        String text = mention + "🚨 KWC keyword alert\n"
                + "[" + scopeLabel + "] " + cleanDiscordUserLine(sender, 80) + ": " + cleanDiscordUserLine(message, 1200) + "\n"
                + "Matched: `" + String.join("`, `", matched.stream().map(v -> cleanDiscordUserLine(v, 80)).toList()) + "`";
        host.discord().sendAdminAlert(c.adminDiscordAlertsChannel, text);
    }

    private boolean scopeEnabled(ConfigValues c, Scope scope) {
        return switch (scope) {
            case PUBLIC -> c.adminDiscordAlertsPublicChat;
            case RELAY -> c.adminDiscordAlertsRelayChat;
            case DM -> c.adminDiscordAlertsDm;
            case GROUP -> c.adminDiscordAlertsGroupChat;
        };
    }

    private List<String> matchedKeywords(ConfigValues c, String input) {
        String haystack = String.valueOf(input == null ? "" : input);
        String target = c.adminDiscordAlertsCaseSensitive ? haystack : haystack.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        List<String> words = c.adminDiscordAlertKeywords == null ? List.of() : c.adminDiscordAlertKeywords;
        for (String raw : words) {
            String word = cleanLine(raw, 80).trim();
            if (word.isBlank()) continue;
            String needle = c.adminDiscordAlertsCaseSensitive ? word : word.toLowerCase(Locale.ROOT);
            if (target.contains(needle)) matched.add(word);
            if (matched.size() >= 12) break;
        }
        return new ArrayList<>(matched);
    }

    private static String safe(String s) { return String.valueOf(s == null ? "" : s); }
    private static String cleanDiscordUserLine(String value, int max) {
        // Only the administrator-configured prefix may create a Discord mention.
        // User-controlled sender/message/keyword text must never turn @everyone,
        // @here, or a role/user mention into a real ping.
        String plain = LegacyText.stripColor(LegacyText.translateAlternateColorCodes('&', safe(value)));
        return cleanLine(plain, max).replace("@", "@\u200B");
    }

    private static String cleanLine(String value, int max) {
        String s = safe(value).replace('\r', ' ').replace('\n', ' ').replace('`', '\'').trim();
        if (s.length() > max) s = s.substring(0, Math.max(0, max - 1)) + "…";
        return s;
    }
}
