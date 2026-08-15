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
            "4.6.1", "config-baselines/config-4.6.1.yml",
            "4.6.2", "config-baselines/config-4.6.2.yml",
            "4.6.3", "config-baselines/config-4.6.3.yml",
            "4.6.4", "config-baselines/config-4.6.4.yml",
            "4.7.0", "config-baselines/config-4.7.0.yml"
    );

    private ConfigMigrationManager() {
    }

    static void check(BlueMapWebChatPlugin plugin) {
        Path configPath = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(configPath)) return;

        String targetVersion = String.valueOf(plugin.getDescription().getVersion()).trim();
        Path reportPath = plugin.getDataFolder().toPath().resolve("config-migration-" + targetVersion + ".yml");
        Path referencePath = plugin.getDataFolder().toPath().resolve("config-reference-" + targetVersion + ".yml");
        Path legacyGuidePath = plugin.getDataFolder().toPath().resolve("config-upgrade-" + targetVersion + ".yml");

        try {
            writeReferenceConfig(plugin, referencePath);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create the full config reference " + referencePath + ": " + ex.getMessage());
        }

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
            writeReport(reportPath, referencePath, configPath, targetVersion, declaredVersion, baselineVersion, diff);
            Files.deleteIfExists(legacyGuidePath);

            String detected = declaredVersion.isBlank() ? "not set" : declaredVersion;
            if (diff.missingSettings.isEmpty() && diff.changedDefaults.isEmpty()) {
                plugin.getLogger().warning("Config version review is required: config-version=" + detected
                        + ", plugin-version=" + targetVersion
                        + ". No other missing settings or changed bundled defaults were found. Existing config setting values were not modified. "
                        + "Review " + reportPath + " and the full current default reference " + referencePath
                        + ", merge its config-version marker only after review, and run /bmchat reload or restart the server.");
            } else {
                plugin.getLogger().warning("Config migration review is required: config-version=" + detected
                        + ", plugin-version=" + targetVersion
                        + ". Existing config setting values were not modified. Review " + reportPath + " and the full current default reference " + referencePath
                        + " (missing=" + diff.missingSettings.size()
                        + ", changed-defaults=" + diff.changedDefaults.size() + ") and set config-version to \""
                        + targetVersion + "\" only after the review is complete.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create the config migration report: " + ex.getMessage());
        }
    }

    private static void writeReferenceConfig(BlueMapWebChatPlugin plugin, Path referencePath) throws Exception {
        byte[] bundled;
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) throw new IllegalStateException("Missing bundled resource: config.yml");
            bundled = in.readAllBytes();
        }
        Files.createDirectories(referencePath.getParent());
        if (Files.isRegularFile(referencePath)) {
            byte[] existing = Files.readAllBytes(referencePath);
            if (java.util.Arrays.equals(existing, bundled)) return;
        }
        Files.write(referencePath, bundled, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
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
            if (yaml.isConfigurationSection(path)) {
                ConfigurationSection section = yaml.getConfigurationSection(path);
                if (section != null && section.getKeys(false).isEmpty()) {
                    out.put(path, new LinkedHashMap<>());
                }
                continue;
            }
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
                                    Path referencePath,
                                    Path configPath,
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
        header.append("# Existing config setting values were not modified by migration.\n");
        header.append("# Before this report, known top-level config blocks may be reordered to the current bundled layout; block text, values, and custom comments are preserved.\n");
        header.append("# Unchanged older bundled comment text may have been refreshed separately; custom comments are preserved.\n");
        header.append("# Only settings missing from config.yml, bundled defaults that changed, and the target config-version marker are listed below.\n");
        header.append("# For the complete current default configuration with every bundled comment, compare against ")
                .append(referencePath.getFileName()).append(".\n");
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

        String lineDiff = buildCommentedLineDiff(configPath, referencePath);
        String body = header + fragment.saveToString();
        if (!body.endsWith("\n")) body += "\n";
        body += "\n" + lineDiff;

        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, body, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String buildCommentedLineDiff(Path configPath, Path referencePath) throws Exception {
        List<String> current = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        List<String> reference = Files.readAllLines(referencePath, StandardCharsets.UTF_8);
        List<LineDiffEntry> entries = lineDiff(current, reference);

        String referenceName = commentValue(referencePath.getFileName());
        StringBuilder out = new StringBuilder();
        out.append("# =============================================================================\n");
        out.append("# Current config.yml vs ").append(referenceName).append(" line diff\n");
        out.append("# =============================================================================\n");
        out.append("# This section is comments only and does not change the YAML fragment above.\n");
        out.append("# Only differing text lines are shown; unchanged lines are omitted.\n");
        out.append("# '-' = text present only in the current config.yml.\n");
        out.append("# '+' = text present only in ").append(referenceName).append(".\n");
        out.append("# File position and line contents are printed on separate lines for readability.\n");
        out.append("# Differing source lines are prefixed with # only, preserving their original indentation exactly.\n");
        out.append("# Line numbers describe config.yml at report-generation time; run /bmchat reload after edits to regenerate them.\n");

        boolean hasChange = entries.stream().anyMatch(entry -> entry.kind != LineDiffKind.SAME);
        if (!hasChange) {
            out.append("# No textual differences found.\n");
            return out.toString();
        }

        int block = 0;
        int i = 0;
        while (i < entries.size()) {
            while (i < entries.size() && entries.get(i).kind == LineDiffKind.SAME) i++;
            if (i >= entries.size()) break;

            int start = i;
            while (i + 1 < entries.size() && entries.get(i + 1).kind != LineDiffKind.SAME) i++;
            int end = i;

            List<LineDiffEntry> currentOnly = new ArrayList<>();
            List<LineDiffEntry> referenceOnly = new ArrayList<>();
            for (int j = start; j <= end; j++) {
                LineDiffEntry entry = entries.get(j);
                if (entry.kind == LineDiffKind.CURRENT_ONLY) currentOnly.add(entry);
                else if (entry.kind == LineDiffKind.REFERENCE_ONLY) referenceOnly.add(entry);
            }

            block++;
            out.append("#\n# Difference ").append(block).append("\n#\n");
            if (!currentOnly.isEmpty()) {
                appendDiffSide(out, "-", "Current config.yml", currentOnly, true);
            }
            if (!currentOnly.isEmpty() && !referenceOnly.isEmpty()) out.append("#\n");
            if (!referenceOnly.isEmpty()) {
                appendDiffSide(out, "+", referenceName, referenceOnly, false);
            }
            if (currentOnly.isEmpty() && !referenceOnly.isEmpty()) {
                int anchor = referenceOnly.get(0).currentAnchorLine;
                out.append("#\n# Insert in Current config.yml\n");
                appendCurrentInsertLocation(out, anchor, current.size());
            }
            i++;
        }
        return out.toString();
    }

    private static void appendDiffSide(StringBuilder out,
                                       String marker,
                                       String fileName,
                                       List<LineDiffEntry> entries,
                                       boolean currentSide) {
        out.append("# ").append(marker).append(" ").append(fileName).append("\n");
        int first = currentSide ? entries.get(0).currentLine : entries.get(0).referenceLine;
        int last = currentSide
                ? entries.get(entries.size() - 1).currentLine
                : entries.get(entries.size() - 1).referenceLine;
        if (first == last) out.append("# Line ").append(first).append("\n");
        else out.append("# Lines ").append(first).append("-").append(last).append("\n");
        for (LineDiffEntry entry : entries) {
            out.append("#").append(diffLineText(entry.text)).append("\n");
        }
    }

    private static void appendCurrentInsertLocation(StringBuilder out, int currentAnchorLine, int currentSize) {
        if (currentAnchorLine <= 0) {
            out.append("# Before Line 1\n");
        } else if (currentAnchorLine >= currentSize) {
            out.append("# After Line ").append(currentSize).append("\n");
        } else {
            out.append("# After Line ").append(currentAnchorLine).append("\n");
        }
    }

    private static List<LineDiffEntry> lineDiff(List<String> current, List<String> reference) {
        long cells = (long) (current.size() + 1) * (reference.size() + 1);
        if (cells <= 2_000_000L) return exactLineDiff(current, reference);
        return boundedLineDiff(current, reference);
    }

    private static List<LineDiffEntry> exactLineDiff(List<String> current, List<String> reference) {
        int n = current.size();
        int m = reference.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (Objects.equals(current.get(i), reference.get(j))) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        List<LineDiffEntry> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && Objects.equals(current.get(i), reference.get(j))) {
                out.add(LineDiffEntry.same(i + 1, j + 1, current.get(i)));
                i++;
                j++;
            } else if (i < n && (j >= m || lcs[i + 1][j] >= lcs[i][j + 1])) {
                out.add(LineDiffEntry.currentOnly(i + 1, j, current.get(i)));
                i++;
            } else {
                out.add(LineDiffEntry.referenceOnly(i, j + 1, reference.get(j)));
                j++;
            }
        }
        return out;
    }

    private static List<LineDiffEntry> boundedLineDiff(List<String> current, List<String> reference) {
        final int lookAhead = 48;
        List<LineDiffEntry> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < current.size() || j < reference.size()) {
            if (i < current.size() && j < reference.size() && Objects.equals(current.get(i), reference.get(j))) {
                out.add(LineDiffEntry.same(i + 1, j + 1, current.get(i)));
                i++;
                j++;
                continue;
            }
            if (i >= current.size()) {
                out.add(LineDiffEntry.referenceOnly(i, j + 1, reference.get(j++)));
                continue;
            }
            if (j >= reference.size()) {
                out.add(LineDiffEntry.currentOnly(i + 1, j, current.get(i++)));
                continue;
            }

            int currentMatch = findAhead(current, i + 1, reference.get(j), lookAhead);
            int referenceMatch = findAhead(reference, j + 1, current.get(i), lookAhead);
            if (currentMatch >= 0 && (referenceMatch < 0 || currentMatch - i <= referenceMatch - j)) {
                while (i < currentMatch) out.add(LineDiffEntry.currentOnly(i + 1, j, current.get(i++)));
            } else if (referenceMatch >= 0) {
                while (j < referenceMatch) out.add(LineDiffEntry.referenceOnly(i, j + 1, reference.get(j++)));
            } else {
                out.add(LineDiffEntry.currentOnly(i + 1, j, current.get(i++)));
                out.add(LineDiffEntry.referenceOnly(i, j + 1, reference.get(j++)));
            }
        }
        return out;
    }

    private static int findAhead(List<String> lines, int start, String target, int maxDistance) {
        int end = Math.min(lines.size(), start + maxDistance);
        for (int i = start; i < end; i++) {
            if (Objects.equals(lines.get(i), target)) return i;
        }
        return -1;
    }

    private static String diffLineText(String value) {
        return String.valueOf(value == null ? "" : value);
    }

    private enum LineDiffKind {
        SAME,
        CURRENT_ONLY,
        REFERENCE_ONLY
    }

    private record LineDiffEntry(LineDiffKind kind,
                                 int currentLine,
                                 int referenceLine,
                                 int currentAnchorLine,
                                 String text) {
        static LineDiffEntry same(int currentLine, int referenceLine, String text) {
            return new LineDiffEntry(LineDiffKind.SAME, currentLine, referenceLine, currentLine, text);
        }

        static LineDiffEntry currentOnly(int currentLine, int referenceAnchorLine, String text) {
            return new LineDiffEntry(LineDiffKind.CURRENT_ONLY, currentLine, referenceAnchorLine, currentLine, text);
        }

        static LineDiffEntry referenceOnly(int currentAnchorLine, int referenceLine, String text) {
            return new LineDiffEntry(LineDiffKind.REFERENCE_ONLY, currentAnchorLine, referenceLine, currentAnchorLine, text);
        }
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
