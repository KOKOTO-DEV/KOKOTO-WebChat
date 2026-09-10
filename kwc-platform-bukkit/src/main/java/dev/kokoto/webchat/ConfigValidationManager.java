package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * ConfigValidationManager는 시작/빌드 시 잘못된 설정을 조기에 찾기 위한 검증 계층이다.
 * ConfigValidationManager is a validation layer used to catch invalid configuration early during startup/build.
 *
 * 검증은 실제 런타임이 허용하는 범위와 일치해야 하며, warning과 hard failure의 경계를 명확히 유지한다.
 * Validation must match what runtime actually accepts, with a clear boundary between warnings and hard failures.
 */
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Validates config.yml before a live reload is allowed to replace the currently
 * running configuration. This prevents a malformed edit from silently falling
 * back to bundled defaults (for example ui.language=en-US).
 */
final class ConfigValidationManager {
    private ConfigValidationManager() {
    }

    static Result validate(KokotoWebChatPlugin plugin) {
        Path path = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(path)) {
            return new Result(false, "config.yml was not found.");
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String indentationProblem = indentationProblem(lines.get(i));
                if (indentationProblem != null) {
                    return new Result(false, indentationProblem + " at Line " + (i + 1) + ". Use normal ASCII spaces for YAML indentation.");
                }
            }

            YamlConfiguration parsed = new YamlConfiguration();
            parsed.load(path.toFile());

            String relayShapeProblem = RelayConfigShapeValidator.problem(parsed.get("server-relay.groups"));
            if (!relayShapeProblem.isBlank()) return new Result(false, relayShapeProblem);

            org.bukkit.configuration.ConfigurationSection custom =
                    parsed.getConfigurationSection("message-tokens.custom");
            if (parsed.contains("message-tokens.custom") && custom == null) {
                return new Result(false, "message-tokens.custom must be a YAML map/object.");
            }
            if (custom != null) {
                for (String key : custom.getKeys(false)) {
                    org.bukkit.configuration.ConfigurationSection item = custom.getConfigurationSection(key);
                    String base = "message-tokens.custom." + key;
                    if (item == null) {
                        return new Result(false, base + " must be a YAML map/object containing aliases and replacement.");
                    }
                    if (item.contains("aliases") && !item.isList("aliases")) {
                        return new Result(false, base + ".aliases must be a YAML list. Both [a, b] and block '- a' forms are supported.");
                    }
                    if (!item.isString("replacement")) {
                        return new Result(false, base + ".replacement must be a string.");
                    }
                }
            }
            return new Result(true, "");
        } catch (InvalidConfigurationException ex) {
            return new Result(false, compactMessage(ex.getMessage(), "Invalid YAML syntax."));
        } catch (IOException ex) {
            return new Result(false, compactMessage(ex.getMessage(), "Could not read config.yml."));
        } catch (RuntimeException ex) {
            return new Result(false, compactMessage(ex.getMessage(), "Could not validate config.yml."));
        }
    }

    private static String indentationProblem(String line) {
        if (line == null || line.isEmpty()) return null;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == ' ') continue;
            if (ch == '\t') return "Tab indentation is not allowed";
            if (ch == '\u3000') return "Full-width space (U+3000) was found in YAML indentation";
            break;
        }
        return null;
    }

    private static String compactMessage(String value, String fallback) {
        String text = String.valueOf(value == null ? "" : value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isBlank()) text = fallback;
        if (text.length() > 700) text = text.substring(0, 700) + "…";
        return text;
    }

    record Result(boolean valid, String message) {
    }
}
