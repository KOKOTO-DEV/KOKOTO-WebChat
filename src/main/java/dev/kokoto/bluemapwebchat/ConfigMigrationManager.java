package dev.kokoto.bluemapwebchat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ConfigMigrationManager {
    private static final List<String> SAFE_DISABLED_WHEN_MISSING = List.of(
            "direct-message.capture-game-whispers",
            "reply.game-click.enabled",
            "reply.game-click.local-game-chat",
            "direct-message.admin-audit.enabled"
    );

    private static final Map<String, String> BASELINE_RESOURCES = Map.of(
            "4.5.5", "config-baselines/config-4.5.5.yml",
            "4.6.0", "config-baselines/config-4.6.0.yml",
            "4.6.1", "config-baselines/config-4.6.1.yml"
    );

    private ConfigMigrationManager() {
    }

    static void check(BlueMapWebChatPlugin plugin) {
        Path configPath = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(configPath)) return;

        String targetVersion = String.valueOf(plugin.getDescription().getVersion()).trim();
        Path reportPath = plugin.getDataFolder().toPath().resolve("config-migration-" + targetVersion + ".yml");
        Path legacyGuidePath = plugin.getDataFolder().toPath().resolve("config-upgrade-" + targetVersion + ".yml");

        try {
            YamlConfiguration actual = YamlConfiguration.loadConfiguration(configPath.toFile());
            String declaredVersion = stringValue(actual.getString("config-version", ""));

            if (targetVersion.equals(declaredVersion)) {
                boolean removedReport = Files.deleteIfExists(reportPath);
                boolean removedLegacyGuide = Files.deleteIfExists(legacyGuidePath);
                if (removedReport || removedLegacyGuide) {
                    plugin.getLogger().info("Config version " + targetVersion
                            + " matches the plugin version. The migration review is considered complete; stale migration guidance was removed.");
                } else {
                    plugin.getLogger().info("Config version " + targetVersion
                            + " matches the plugin version. Migration comparison was skipped because this config is already marked as reviewed.");
                }
                return;
            }

            applyMigrationSafeRuntimeFallbacks(plugin, actual);

            YamlConfiguration currentDefaults = loadBundledYaml(plugin, "config.yml");
            String baselineVersion = chooseBaselineVersion(declaredVersion);
            YamlConfiguration previousDefaults = baselineVersion.isBlank()
                    ? new YamlConfiguration()
                    : loadBundledYaml(plugin, BASELINE_RESOURCES.get(baselineVersion));

            MigrationDiff diff = compare(actual, previousDefaults, currentDefaults);
            writeReport(reportPath, targetVersion, declaredVersion, baselineVersion, diff);
            Files.deleteIfExists(legacyGuidePath);

            String detected = declaredVersion.isBlank() ? "not set" : declaredVersion;
            if (diff.missingSettings.isEmpty() && diff.changedDefaults.isEmpty()) {
                plugin.getLogger().warning("Config version review is required: config-version=" + detected
                        + ", plugin-version=" + targetVersion
                        + ". No other missing settings or changed bundled defaults were found. Existing config.yml was not modified. "
                        + "Review " + reportPath + ", merge its config-version marker, and run /bmchat reload or restart the server.");
            } else {
                plugin.getLogger().warning("Config migration review is required: config-version=" + detected
                        + ", plugin-version=" + targetVersion
                        + ". Existing config.yml was not modified. Review " + reportPath
                        + " (missing=" + diff.missingSettings.size()
                        + ", changed-defaults=" + diff.changedDefaults.size() + ") and set config-version to \""
                        + targetVersion + "\" only after the review is complete.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create the config migration report: " + ex.getMessage());
        }
    }

    private static void applyMigrationSafeRuntimeFallbacks(BlueMapWebChatPlugin plugin, YamlConfiguration actual) {
        for (String path : SAFE_DISABLED_WHEN_MISSING) {
            if (!actual.isSet(path)) plugin.getConfig().set(path, false);
        }
    }

    private static String chooseBaselineVersion(String declaredVersion) {
        if (BASELINE_RESOURCES.containsKey(declaredVersion)) return declaredVersion;
        // 4.6.0 is the first version with a config-version marker. An absent marker
        // therefore normally means a 4.5.5-or-older config. For an explicit but
        // unknown version, do not guess changed defaults; only report missing keys.
        if (declaredVersion.isBlank() && BASELINE_RESOURCES.containsKey("4.5.5")) return "4.5.5";
        return "";
    }

    private static YamlConfiguration loadBundledYaml(BlueMapWebChatPlugin plugin, String resource) throws Exception {
        if (resource == null || resource.isBlank()) return new YamlConfiguration();
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) throw new IllegalStateException("Missing bundled resource: " + resource);
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        }
    }

    private static MigrationDiff compare(YamlConfiguration actual,
                                         YamlConfiguration previousDefaults,
                                         YamlConfiguration currentDefaults) {
        Map<String, Object> previous = leafValues(previousDefaults);
        Map<String, Object> current = leafValues(currentDefaults);
        Map<String, Object> physical = leafValues(actual);

        LinkedHashMap<String, Object> missing = new LinkedHashMap<>();
        List<Map<String, Object>> changed = new ArrayList<>();

        for (Map.Entry<String, Object> entry : current.entrySet()) {
            String path = entry.getKey();
            if (path.equals("config-version")) continue;
            if (!physical.containsKey(path)) {
                missing.put(path, entry.getValue());
                continue;
            }
            if (!previous.containsKey(path)) continue;

            Object oldDefault = previous.get(path);
            Object newDefault = entry.getValue();
            if (valuesEqual(oldDefault, newDefault)) continue;

            Object configured = physical.get(path);
            if (!valuesEqual(configured, oldDefault)) continue;

            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("path", path);
            item.put("configured-value", configured);
            item.put("previous-default", oldDefault);
            item.put("current-default", newDefault);
            changed.add(item);
        }

        return new MigrationDiff(missing, changed);
    }

    private static Map<String, Object> leafValues(YamlConfiguration yaml) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        Set<String> paths = new LinkedHashSet<>(yaml.getKeys(true));
        for (String path : paths) {
            if (yaml.isConfigurationSection(path)) continue;
            out.put(path, normalizeValue(yaml.get(path)));
        }
        return out;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) out.put(key, normalizeValue(section.get(key)));
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(normalizeValue(item));
            return out;
        }
        return value;
    }

    private static boolean valuesEqual(Object left, Object right) {
        return Objects.equals(normalizeValue(left), normalizeValue(right));
    }

    private static void writeReport(Path reportPath,
                                    String targetVersion,
                                    String declaredVersion,
                                    String baselineVersion,
                                    MigrationDiff diff) throws Exception {
        YamlConfiguration fragment = new YamlConfiguration();
        for (Map.Entry<String, Object> entry : diff.missingSettings.entrySet()) {
            fragment.set(entry.getKey(), entry.getValue());
        }
        for (Map<String, Object> item : diff.changedDefaults) {
            fragment.set(String.valueOf(item.get("path")), item.get("current-default"));
        }
        // Always include the review marker when the declared version is absent or different.
        // This keeps configuration version management explicit even when there are no other
        // missing settings or changed defaults.
        fragment.set("config-version", targetVersion);

        String detected = declaredVersion.isBlank() ? "not set" : declaredVersion;
        String baseline = baselineVersion.isBlank() ? "not available" : baselineVersion;
        StringBuilder header = new StringBuilder();
        header.append("# BlueMapWebChat configuration migration fragment\n");
        header.append("# Existing config.yml was not modified.\n");
        header.append("# Only settings missing from config.yml, bundled defaults that changed, and the target config-version marker are listed below.\n");
        header.append("# Merge the required values into the matching locations in config.yml.\n");
        header.append("# Detected config version: ").append(commentValue(detected)).append("\n");
        header.append("# Target plugin version: ").append(commentValue(targetVersion)).append("\n");
        header.append("# Comparison baseline: ").append(commentValue(baseline)).append("\n");
        header.append("# Missing settings: ").append(diff.missingSettings.size()).append("\n");
        header.append("# Changed defaults: ").append(diff.changedDefaults.size()).append("\n");

        if (!diff.changedDefaults.isEmpty()) {
            header.append("#\n# Changed bundled defaults included in the fragment:\n");
            for (Map<String, Object> item : diff.changedDefaults) {
                header.append("# - ").append(commentValue(item.get("path"))).append("\n");
                header.append("#   configured and previous default: ")
                        .append(commentValue(item.get("previous-default"))).append("\n");
                header.append("#   new bundled default: ")
                        .append(commentValue(item.get("current-default"))).append("\n");
            }
        }

        header.append("#\n# The config-version entry below is the final review marker. Merge it only after reviewing the fragment.\n\n");

        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, header + fragment.saveToString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String commentValue(Object value) {
        String text = String.valueOf(value == null ? "null" : value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        if (text.length() > 240) text = text.substring(0, 237) + "...";
        return text;
    }

    private static String stringValue(String value) {
        return value == null ? "" : value.trim();
    }

    private record MigrationDiff(LinkedHashMap<String, Object> missingSettings,
                                 List<Map<String, Object>> changedDefaults) {
    }
}
