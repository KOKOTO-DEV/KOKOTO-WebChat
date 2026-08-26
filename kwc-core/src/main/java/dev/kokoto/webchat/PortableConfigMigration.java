package dev.kokoto.webchat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loader-neutral configuration migration used by Bukkit, Fabric, NeoForge and Forge.
 *
 * The bundled config.yml is the only migration template. Existing operator values
 * are overlaid on that fresh template; generated config-reference files are
 * administrator-readable copies only and are never used as migration input.
 */
public final class PortableConfigMigration {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String RETIRED_SETTING = "ui.show-login-only-when-hidden";

    private static final Map<String, String> BASELINE_RESOURCES = Map.of(
            "4.5.5", "config-baselines/config-4.5.5.yml",
            "4.6.0", "config-baselines/config-4.6.0.yml",
            "4.6.1", "config-baselines/config-4.6.1.yml",
            "4.6.2", "config-baselines/config-4.6.2.yml",
            "4.6.3", "config-baselines/config-4.6.3.yml",
            "4.6.4", "config-baselines/config-4.6.4.yml",
            "4.7.0", "config-baselines/config-4.7.0.yml",
            "5.0.0", "config-baselines/config-5.0.0.yml"
    );

    private PortableConfigMigration() {}

    @FunctionalInterface
    public interface ResourceOpener {
        InputStream open(String resourceName) throws Exception;
    }

    /** Returns dot-path leaf values, including empty mappings when practical. */
    @FunctionalInterface
    public interface SnapshotLoader {
        Map<String, Object> load(InputStream input) throws Exception;
    }

    @FunctionalInterface
    public interface Logger {
        void log(String message);
    }

    public record Result(boolean changed, boolean automaticMigrationEnabled, int insertedSettings) {}

    public static Result reconcile(Path dataDirectory,
                                   String targetVersion,
                                   ResourceOpener opener,
                                   SnapshotLoader loader,
                                   Logger info) throws Exception {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(opener, "opener");
        Objects.requireNonNull(loader, "loader");
        Path configPath = dataDirectory.resolve("config.yml");
        if (!Files.isRegularFile(configPath)) return new Result(false, false, 0);

        String target = safe(targetVersion);
        if (target.isBlank()) throw new IllegalArgumentException("targetVersion is blank");
        String autoVersion = target + "_auto_migration";
        Path reportPath = dataDirectory.resolve("config-migration-" + target + ".yml");
        Path referencePath = dataDirectory.resolve("config-reference-" + target + ".yml");
        Path legacyGuidePath = dataDirectory.resolve("config-upgrade-" + target + ".yml");

        cleanupOldGeneratedMigrationFiles(dataDirectory, target);

        byte[] bundledBytes = resourceBytes(opener, "config.yml");
        writeReference(referencePath, bundledBytes);
        String bundledText = new String(bundledBytes, StandardCharsets.UTF_8);

        // Exact <version> is a fixed operator config, so do not refresh comments, order,
        // indentation or retired keys before deciding whether migration is active.
        Map<String,Object> actual = loadSnapshot(loader, Files.newInputStream(configPath));
        String declared = safe(string(actual.get("config-version")));

        if (target.equals(declared)) {
            boolean removed = Files.deleteIfExists(reportPath) | Files.deleteIfExists(legacyGuidePath);
            if (info != null) info.log(removed
                    ? "Config version " + target + " has automatic migration disabled. Stale migration files were removed."
                    : "Config version " + target + " has automatic migration disabled. Migration comparison was skipped.");
            return new Result(false, false, 0);
        }

        Map<String,Object> currentDefaults = loadSnapshot(loader, opener.open("config.yml"));
        String beforeText = Files.readString(configPath, StandardCharsets.UTF_8);

        if (autoVersion.equals(declared)) {
            MigrationDiff before = compare(actual, currentDefaults, currentDefaults);
            List<String> newlyInserted = new ArrayList<>(before.missingSettings.keySet());
            migratePhysical(configPath, bundledText, actual, autoVersion);
            String afterText = Files.readString(configPath, StandardCharsets.UTF_8);
            Map<String,Object> refreshed = loadSnapshot(loader, Files.newInputStream(configPath));
            MigrationDiff current = compare(refreshed, currentDefaults, currentDefaults);
            writeReport(reportPath, referencePath, configPath, target, autoVersion,
                    target + " (same-version automatic migration)", current, newlyInserted.size(), refreshed, currentDefaults);
            Files.deleteIfExists(legacyGuidePath);
            if (info != null) info.log("Config automatic migration is enabled: config-version=\"" + autoVersion
                    + "\". Rebuilt from the current bundled config and overlaid existing values; newly inserted settings=" + newlyInserted.size()
                    + ". Existing configured values were preserved. Set config-version to \"" + target
                    + "\" only if same-version automatic migration should be disabled.");
            return new Result(!afterText.equals(beforeText), true, newlyInserted.size());
        }

        String baselineVersion = chooseBaselineVersion(declared);
        Map<String,Object> previousDefaults = baselineVersion.isBlank()
                ? Map.of()
                : loadSnapshot(loader, opener.open(BASELINE_RESOURCES.get(baselineVersion)));
        MigrationDiff before = compare(actual, previousDefaults, currentDefaults);
        if (!declared.endsWith("_auto_migration")) {
            Path backup = backupFixedConfig(configPath, target);
            if (info != null) info.log("Backed up fixed config before version migration: " + backup.getFileName());
        }
        migratePhysical(configPath, bundledText, actual, autoVersion);
        Map<String,Object> migrated = loadSnapshot(loader, Files.newInputStream(configPath));
        if (!autoVersion.equals(safe(string(migrated.get("config-version"))))) {
            throw new IllegalStateException("migration marker was not persisted");
        }
        MigrationDiff after = compare(migrated, previousDefaults, currentDefaults);
        Files.deleteIfExists(legacyGuidePath);
        writeReport(reportPath, referencePath, configPath, target, declared, baselineVersion, after,
                before.missingSettings.size(), migrated, currentDefaults);
        if (info != null) info.log("Config migration completed: detected=" + (declared.isBlank() ? "not set" : declared)
                + ", target=" + target + ", baseline=" + (baselineVersion.isBlank() ? "not available" : baselineVersion)
                + ", auto-inserted=" + before.missingSettings.size() + ", changed-default-review=" + after.changedDefaults.size()
                + "; leave config-version as \"" + autoVersion + "\" to keep automatic migration, or set it to \""
                + target + "\" to disable same-version automatic migration.");
        return new Result(true, true, before.missingSettings.size());
    }

    private static void migratePhysical(Path configPath,
                                        String bundledText,
                                        Map<String,Object> actual,
                                        String autoVersion) throws Exception {
        // Reconstruct from the current bundled default every time migration is active.
        // Old comments/layout are intentionally discarded; only operator values survive.
        Files.writeString(configPath, bundledText, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        LinkedHashMap<String,Object> overlay = new LinkedHashMap<>();
        if (actual != null) overlay.putAll(actual);
        overlay.remove("config-version");
        overlay.remove(RETIRED_SETTING);
        if (!overlay.isEmpty()) ConfigTextEditor.setValues(configPath, overlay);
        ConfigTextEditor.setScalar(configPath, "config-version", autoVersion);
    }

    private static Path backupFixedConfig(Path configPath, String targetVersion) throws Exception {
        String stamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
        String base = "config-backup-before-" + targetVersion + "-" + stamp;
        Path parent = configPath.getParent();
        Path backup = parent.resolve(base + ".yml");
        int suffix = 2;
        while (Files.exists(backup)) backup = parent.resolve(base + "-" + suffix++ + ".yml");
        Files.copy(configPath, backup, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    private static void cleanupOldGeneratedMigrationFiles(Path dataDirectory, String targetVersion) throws Exception {
        if (!Files.isDirectory(dataDirectory)) return;
        String keepReference = "config-reference-" + targetVersion + ".yml";
        String keepMigration = "config-migration-" + targetVersion + ".yml";
        String keepUpgrade = "config-upgrade-" + targetVersion + ".yml";
        try (var stream = Files.list(dataDirectory)) {
            for (Path path : stream.toList()) {
                if (!Files.isRegularFile(path)) continue;
                String name = String.valueOf(path.getFileName());
                boolean generated = (name.startsWith("config-reference-")
                        || name.startsWith("config-migration-")
                        || name.startsWith("config-upgrade-")) && name.endsWith(".yml");
                if (!generated) continue;
                if (name.equals(keepReference) || name.equals(keepMigration) || name.equals(keepUpgrade)) continue;
                Files.deleteIfExists(path);
            }
        }
    }

    private static Map<String,Object> loadSnapshot(SnapshotLoader loader, InputStream input) throws Exception {
        if (input == null) return Map.of();
        try (InputStream in = input) {
            Map<String,Object> map = loader.load(in);
            return map == null ? Map.of() : normalizeFlat(map);
        }
    }

    private static byte[] resourceBytes(ResourceOpener opener, String name) throws Exception {
        try (InputStream in = opener.open(name)) {
            if (in == null) throw new IllegalStateException("Missing bundled resource: " + name);
            return in.readAllBytes();
        }
    }

    private static void writeReference(Path path, byte[] bytes) throws Exception {
        Files.createDirectories(path.getParent());
        if (Files.isRegularFile(path) && Arrays.equals(Files.readAllBytes(path), bytes)) return;
        Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String chooseBaselineVersion(String declaredVersion) {
        String declared = safe(declaredVersion);
        if (BASELINE_RESOURCES.containsKey(declared)) return declared;
        if (declared.endsWith("_auto_migration")) {
            String base = declared.substring(0, declared.length() - "_auto_migration".length());
            if (BASELINE_RESOURCES.containsKey(base)) return base;
        }
        if (declared.isBlank() && BASELINE_RESOURCES.containsKey("4.5.5")) return "4.5.5";
        return "";
    }

    private static MigrationDiff compare(Map<String,Object> actual,
                                         Map<String,Object> previous,
                                         Map<String,Object> current) {
        LinkedHashMap<String,Object> missing = new LinkedHashMap<>();
        List<Map<String,Object>> changed = new ArrayList<>();
        for (Map.Entry<String,Object> entry : current.entrySet()) {
            String path = entry.getKey();
            if ("config-version".equals(path)) continue;
            if (!actual.containsKey(path)) {
                missing.put(path, entry.getValue());
                continue;
            }
            if (!previous.containsKey(path)) continue;
            Object oldDefault = previous.get(path);
            Object newDefault = entry.getValue();
            if (valuesEqual(oldDefault, newDefault)) continue;
            Object configured = actual.get(path);
            if (!valuesEqual(configured, oldDefault)) continue;
            LinkedHashMap<String,Object> item = new LinkedHashMap<>();
            item.put("path", path);
            item.put("configured-value", configured);
            item.put("previous-default", oldDefault);
            item.put("current-default", newDefault);
            changed.add(item);
        }
        return new MigrationDiff(missing, changed);
    }

    private static LinkedHashMap<String,Object> scalarLeaves(Map<String,Object> values) {
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        for (Map.Entry<String,Object> e : values.entrySet()) {
            Object v = e.getValue();
            if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean) out.put(e.getKey(), v);
        }
        return out;
    }

    private static Map<String,Object> normalizeFlat(Map<String,Object> values) {
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        for (Map.Entry<String,Object> e : values.entrySet()) out.put(String.valueOf(e.getKey()), normalizeValue(e.getValue()));
        return out;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?,?> map) {
            LinkedHashMap<String,Object> out = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : map.entrySet()) out.put(String.valueOf(e.getKey()), normalizeValue(e.getValue()));
            return out;
        }
        if (value instanceof List<?> list) {
            ArrayList<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(normalizeValue(item));
            return out;
        }
        return value;
    }

    private static boolean valuesEqual(Object a, Object b) {
        return Objects.equals(normalizeValue(a), normalizeValue(b));
    }

    private static void writeReport(Path reportPath,
                                    Path referencePath,
                                    Path configPath,
                                    String targetVersion,
                                    String declaredVersion,
                                    String baselineVersion,
                                    MigrationDiff diff,
                                    int autoInsertedSettings,
                                    Map<String,Object> currentValues,
                                    Map<String,Object> bundledDefaults) throws Exception {
        String detected = safe(declaredVersion).isBlank() ? "not set" : safe(declaredVersion);
        String baseline = safe(baselineVersion).isBlank() ? "not available" : safe(baselineVersion);
        StringBuilder header = new StringBuilder();
        header.append("# KOKOTO WebChat configuration migration report\n");
        header.append("# Migration reconstructs config.yml from the current bundled default and overlays the existing configured values.\n");
        header.append("# Existing configured values are preserved. Old comments/order/whitespace/indentation are discarded and replaced by the current bundled config layout.\n");
        header.append("# config-reference-<version>.yml is only the administrator-readable exact bundled default copy; it is not the migration template.\n");
        header.append("# The migrated config is marked <version>_auto_migration. While this marker remains, startup/reload repeats the bundled-default rebuild with current values overlaid.\n");
        header.append("# This marker is an automatic-migration preference, not a review-status marker: it may remain after the operator has reviewed the configuration.\n");
        header.append("# To disable same-version automatic migration, change config-version to the exact plugin version.\n");
        header.append("# Existing operator values are preserved; the current setting-value review is regenerated from the current state.\n");
        header.append("# For the complete current default configuration with every bundled comment, compare against ").append(referencePath.getFileName()).append(".\n");
        header.append("# Detected config version: ").append(commentValue(detected)).append("\n");
        header.append("# Target plugin version: ").append(commentValue(targetVersion)).append("\n");
        header.append("# Comparison baseline: ").append(commentValue(baseline)).append("\n");
        header.append("# Settings supplied by the new bundled default in this migration: ").append(autoInsertedSettings).append("\n");
        header.append("# Missing settings still unresolved: ").append(diff.missingSettings.size()).append("\n");
        header.append("# Changed defaults requiring manual review: ").append(diff.changedDefaults.size()).append("\n");
        header.append("# Automatic migration: ENABLED\n");
        if (!diff.changedDefaults.isEmpty()) {
            header.append("#\n# Changed bundled defaults included in the fragment:\n");
            for (Map<String,Object> item : diff.changedDefaults) {
                header.append("# - ").append(commentValue(item.get("path"))).append("\n");
                header.append("#   configured and previous default: ").append(commentValue(item.get("previous-default"))).append("\n");
                header.append("#   new bundled default: ").append(commentValue(item.get("current-default"))).append("\n");
            }
        }
        if (!diff.missingSettings.isEmpty()) {
            header.append("#\n# Missing settings still unresolved (normally this should be empty after automatic insertion):\n");
            for (String path : diff.missingSettings.keySet()) header.append("# - ").append(commentValue(path)).append("\n");
        }
        header.append("#\n# The config-version entry below is the automatic-migration stop marker.\n");
        header.append("# Apply this exact value only if you want to disable further same-version automatic migration.\n");
        header.append("# Otherwise leave config.yml as ").append(targetVersion).append("_auto_migration.\n\n");

        String fragment = reportFragment(diff.changedDefaults, targetVersion);
        String body = header + fragment;
        if (!body.endsWith("\n")) body += "\n";
        body += "\n" + buildSemanticSettingDiff(configPath, referencePath, currentValues, bundledDefaults);
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, body, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String reportFragment(List<Map<String,Object>> changedDefaults, String targetVersion) throws Exception {
        Path tmp = Files.createTempFile("kwc-config-migration-fragment-", ".yml");
        try {
            Files.writeString(tmp, "", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            LinkedHashMap<String,Object> values = new LinkedHashMap<>();
            for (Map<String,Object> item : changedDefaults) values.put(String.valueOf(item.get("path")), item.get("current-default"));
            if (!values.isEmpty()) ConfigTextEditor.setValues(tmp, values);
            ConfigTextEditor.setScalar(tmp, "config-version", targetVersion);
            return Files.readString(tmp, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String buildSemanticSettingDiff(Path configPath,
                                                   Path referencePath,
                                                   Map<String,Object> currentValues,
                                                   Map<String,Object> bundledDefaults) throws Exception {
        Map<String,Object> current = normalizeFlat(currentValues == null ? Map.of() : currentValues);
        Map<String,Object> defaults = normalizeFlat(bundledDefaults == null ? Map.of() : bundledDefaults);
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.addAll(defaults.keySet());
        for (String path : current.keySet()) if (!paths.contains(path)) paths.add(path);

        ArrayList<String> changedPaths = new ArrayList<>();
        for (String path : paths) {
            boolean inCurrent = current.containsKey(path);
            boolean inDefault = defaults.containsKey(path);
            if (inCurrent != inDefault || !valuesEqual(current.get(path), defaults.get(path))) changedPaths.add(path);
        }

        String referenceName = commentValue(referencePath.getFileName());
        StringBuilder out = new StringBuilder();
        out.append("# =============================================================================\n");
        out.append("# Current config.yml vs ").append(referenceName).append(" setting diff\n");
        out.append("# =============================================================================\n");
        out.append("# Comparison is YAML-setting based, not whole-file text based.\n");
        out.append("# Comments, blank lines, indentation, quoting style, and shifted line positions are ignored.\n");
        out.append("# Only settings whose effective values differ from the bundled default are shown.\n");
        out.append("# Line numbers are looked up independently in each file after migration.\n");
        if (changedPaths.isEmpty()) return out.append("# No setting-value differences found.\n").toString();

        Map<String,ConfigTextEditor.ValueBlock> currentBlocks = ConfigTextEditor.readValueBlocks(configPath, changedPaths);
        Map<String,ConfigTextEditor.ValueBlock> referenceBlocks = ConfigTextEditor.readValueBlocks(referencePath, changedPaths);
        int block = 0;
        for (String path : changedPaths) {
            ConfigTextEditor.ValueBlock left = currentBlocks.get(path);
            ConfigTextEditor.ValueBlock right = referenceBlocks.get(path);
            block++;
            out.append("# Difference ").append(block).append("\n# Setting: ").append(commentValue(path)).append("\n#\n");
            if (left != null) appendValueBlock(out, "-", "Current config.yml", left);
            else out.append("# - Current config.yml\n# Missing setting\n");
            out.append("#\n");
            if (right != null) appendValueBlock(out, "+", referenceName, right);
            else out.append("# + ").append(referenceName).append("\n# Setting is not present in the bundled default (operator/custom setting).\n");
            out.append("#\n#\n#\n");
        }
        return out.toString();
    }

    private static void appendValueBlock(StringBuilder out,
                                         String marker,
                                         String fileName,
                                         ConfigTextEditor.ValueBlock block) {
        out.append("# ").append(marker).append(" ").append(fileName).append("\n");
        if (block.startLine() == block.endLine()) out.append("# Line ").append(block.startLine()).append("\n");
        else out.append("# Lines ").append(block.startLine()).append("-").append(block.endLine()).append("\n");
        for (String line : block.lines()) out.append("#").append(line == null ? "" : line).append("\n");
    }

    private record MigrationDiff(LinkedHashMap<String,Object> missingSettings,List<Map<String,Object>> changedDefaults){}

    private static String commentValue(Object value) {
        String text=String.valueOf(value==null?"null":value).replace('\r',' ').replace('\n',' ').trim();
        return text.length()>240 ? text.substring(0,237)+"..." : text;
    }
    private static String string(Object value){ return value==null?"":String.valueOf(value); }
    private static String safe(String value){ return value==null?"":value.trim(); }
}
