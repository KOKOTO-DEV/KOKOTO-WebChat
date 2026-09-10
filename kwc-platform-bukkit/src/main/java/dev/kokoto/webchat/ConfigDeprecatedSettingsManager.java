package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * ConfigDeprecatedSettingsManager는 KWC 설정을 core가 사용할 수 있는 형태로 읽거나 보관하는 설정 계층이다.
 * ConfigDeprecatedSettingsManager is part of the configuration layer that reads or carries KWC settings in a core-friendly form.
 *
 * 설정 키를 바꿀 때는 canonical config, 과거 baseline, migration, 다국어 template, 문서 reference가 함께 움직여야 한다.
 * When changing a setting key, update canonical config, historical baselines, migration, localized templates, and documentation references together.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes settings that have been retired from the current configuration while
 * preserving the rest of config.yml as text. This intentionally avoids YAML
 * re-serialization so user comments, quoting, spacing, and setting order remain
 * untouched.
 */
final class ConfigDeprecatedSettingsManager {
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*:(?:\\s.*)?$");
    private static final Pattern SHOW_LOGIN_ONLY = Pattern.compile("^\\s+show-login-only-when-hidden\\s*:.*$");
    private static final Pattern OLD_RELAY_FORWARD = Pattern.compile("^\\s+forward-received-public-chat\\s*:.*$");

    private ConfigDeprecatedSettingsManager() {
    }

    static void remove(KokotoWebChatPlugin plugin) {
        Path path = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(path)) return;
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            String updated = removeDeprecatedText(raw);
            if (updated.equals(raw)) return;
            Files.writeString(path, updated, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            plugin.getLogger().info("Removed deprecated config setting(s). Relay v2 forwarding is configured per group at "
                    + "server-relay.groups[].forwarding.enabled; guest visibility uses guest.enabled and "
                    + "ui.hide-chat-for-guests-when-guest-disabled.");
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to remove deprecated config settings: " + ex.getMessage());
        }
    }

    static String removeDeprecatedText(String input) {
        String raw = input == null ? "" : input;
        if (raw.isEmpty()) return raw;
        String newline = raw.contains("\r\n") ? "\r\n" : "\n";
        boolean endedWithNewline = raw.endsWith("\n") || raw.endsWith("\r");
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] split = normalized.split("\\n", -1);
        List<String> out = new ArrayList<>(split.length);
        String topLevel = "";
        boolean removed = false;

        int limit = split.length;
        if (limit > 0 && split[limit - 1].isEmpty()) limit--;
        for (int i = 0; i < limit; i++) {
            String line = split[i];
            Matcher top = TOP_LEVEL_KEY.matcher(line);
            if (top.matches()) topLevel = top.group(1);
            if ("ui".equals(topLevel) && SHOW_LOGIN_ONLY.matcher(line).matches()) {
                removed = true;
                continue;
            }
            if ("server-relay".equals(topLevel) && OLD_RELAY_FORWARD.matcher(line).matches()) {
                removed = true;
                continue;
            }
            out.add(line);
        }
        if (!removed) return raw;

        String result = String.join("\n", out);
        if (endedWithNewline) result += "\n";
        if (!"\n".equals(newline)) result = result.replace("\n", newline);
        return result;
    }
}
