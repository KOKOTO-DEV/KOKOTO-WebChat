package dev.kokoto.webchat;

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
            plugin.getLogger().info("Removed deprecated config setting: ui.show-login-only-when-hidden. "
                    + "Guest chat visibility is now controlled only by guest.enabled and "
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
            out.add(line);
        }
        if (!removed) return raw;

        String result = String.join("\n", out);
        if (endedWithNewline) result += "\n";
        if (!"\n".equals(newline)) result = result.replace("\n", newline);
        return result;
    }
}
