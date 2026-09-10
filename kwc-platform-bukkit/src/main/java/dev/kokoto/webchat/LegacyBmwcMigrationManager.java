package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * LegacyBmwcMigrationManager는 이전 KWC/BMWC 설치 데이터를 현재 형식으로 옮기는 migration 코드다.
 * LegacyBmwcMigrationManager migrates older KWC/BMWC installation data into the current format.
 *
 * 기존 관리자 값을 가능한 한 보존하고, 한 번 적용한 migration을 재실행해도 결과가 달라지지 않는 idempotency를 유지해야 한다.
 * It should preserve administrator choices where possible and remain idempotent when the same migration is evaluated again.
 */
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One-time import from the BlueMapWebChat 4.x data/config layout. */
final class LegacyBmwcMigrationManager {
    private static final String LEGACY_DATA_DIR = "BlueMapWebChat";
    private static final String MARKER = ".legacy-import-complete";
    private static final Map<String, String> LEGACY_BASELINES = legacyBaselines();

    private LegacyBmwcMigrationManager() {}

    /**
     * Imports BlueMapWebChat data only into a genuinely empty KWC data directory.
     *
     * <p>The marker is only temporary migration state. It is never the safety
     * boundary: if KWC already has any real file, legacy data is not merged even
     * when the marker was deleted manually. Once the legacy source directory is
     * removed, the marker is cleaned up automatically.</p>
     *
     * @return true when the old config should be converted after saveDefaultConfig().
     */
    static boolean prepare(KokotoWebChatPlugin plugin) {
        Path target = plugin.getDataFolder().toPath();
        Path legacy = legacyDataDirectory(plugin);
        if (legacy == null || !Files.isDirectory(legacy)) {
            cleanupMarkerIfSourceGone(plugin);
            return false;
        }
        try {
            Files.createDirectories(target);
            if (Files.isRegularFile(target.resolve(MARKER))) return false;

            // Never merge legacy BMWC data into an existing KWC installation.
            // This guard is deliberately independent of the marker so deleting
            // .legacy-import-complete cannot cause an old database/JSONL/config
            // to be imported again over a live KWC data set.
            if (hasExistingKwcData(target)) {
                writeMarker(plugin, target,
                        "Legacy import skipped because KOKOTO-WebChat data already exists.\n");
                plugin.getLogger().info("Skipped BlueMapWebChat legacy import because plugins/KOKOTO-WebChat already contains data.");
                return false;
            }

            copyOperationalData(legacy, target);
            boolean importConfig = Files.isRegularFile(legacy.resolve("config.yml"));
            if (!importConfig) writeMarker(plugin, target);
            return importConfig;
        } catch (Exception ex) {
            plugin.getLogger().warning("Legacy BlueMapWebChat data import failed: " + ex.getMessage());
            return false;
        }
    }

    /** Remove the temporary import marker after the BMWC migration source is gone. */
    static void cleanupMarkerIfSourceGone(KokotoWebChatPlugin plugin) {
        Path legacy = legacyDataDirectory(plugin);
        if (legacy != null && Files.isDirectory(legacy)) return;
        Path marker = plugin.getDataFolder().toPath().resolve(MARKER);
        try {
            if (Files.deleteIfExists(marker)) {
                plugin.getLogger().info("Removed .legacy-import-complete because the BlueMapWebChat migration source no longer exists.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not remove obsolete .legacy-import-complete: " + ex.getMessage());
        }
    }

    static void convertLegacyConfig(KokotoWebChatPlugin plugin) {
        Path legacy = legacyDataDirectory(plugin);
        if (legacy == null) return;
        Path legacyConfig = legacy.resolve("config.yml");
        if (!Files.isRegularFile(legacyConfig)) return;
        Path targetConfig = plugin.getDataFolder().toPath().resolve("config.yml");
        try {
            convertConfig(plugin, legacyConfig, targetConfig);
            writeMarker(plugin, plugin.getDataFolder().toPath());
            plugin.getLogger().info("Migrated legacy BlueMapWebChat config/data to plugins/KOKOTO-WebChat. "
                    + "The original plugins/BlueMapWebChat directory was left unchanged as the migration source/backup.");
            plugin.getLogger().warning("BMWC HTTPS routes are external and were not changed automatically. "
                    + "If you used /bmwc/api and /bmwc/chat, update Caddy/nginx to the KWC /chat layout "
                    + "(standalone /chat, API /chat/api; strip /chat before proxying to port 8899).");
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to convert legacy BlueMapWebChat config.yml: " + ex.getMessage());
        }
    }

    static boolean convertCurrentConfigIfLegacy(KokotoWebChatPlugin plugin) {
        Path current = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(current)) return false;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(current.toFile());
            if (!yaml.isSet("web-addon") && !yaml.isSet("standalone-web")) return false;
            convertConfig(plugin, current, current);
            plugin.getLogger().info("Converted legacy BlueMapWebChat config keys in plugins/KOKOTO-WebChat/config.yml to the KWC layout.");
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to convert legacy config keys in KOKOTO-WebChat/config.yml: " + ex.getMessage());
            return false;
        }
    }

    private static void convertConfig(KokotoWebChatPlugin plugin, Path sourceConfig, Path targetConfig) throws Exception {
        YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(sourceConfig.toFile());
        YamlConfiguration target;
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) throw new IllegalStateException("Missing bundled config.yml");
            target = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        YamlConfiguration previousDefaults = loadPreviousDefaults(plugin, oldConfig);
        LinkedHashMap<String,Object> migratedValues = new LinkedHashMap<>();
        for (String path : oldConfig.getKeys(true)) {
            if (oldConfig.isConfigurationSection(path)) continue;
            String mapped = mapPath(path);
            if (mapped.isBlank()) continue;
            if (!isKnownTargetPath(target, mapped)) continue;
            Object legacyValue = oldConfig.get(path);
            // If the old value is still exactly the bundled default from that BMWC
            // release, keep the current KWC default instead. This prevents old default
            // values such as standalone-web.path=/chat from overwriting the new split
            // layout (frontend.standalone.path=/ + http.public-prefix=/chat).
            if (matchesPreviousDefault(previousDefaults, path, legacyValue)) continue;
            // Relay v1 trust settings are not known target paths in 5.1.0 and are
            // intentionally not migrated. Relay v2 groups must be configured explicitly.
            migratedValues.put(mapped, migrateMappedValue(oldConfig, path, mapped, legacyValue));
        }

        // Start from the exact current bundled text instead of YamlConfiguration.save().
        // This preserves the reference comments/order/quote style and avoids a migration
        // diff made almost entirely of serializer formatting changes. Overlay only the
        // user-custom legacy values with the comment-preserving text editor.
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) throw new IllegalStateException("Missing bundled config.yml");
            Files.createDirectories(targetConfig.toAbsolutePath().getParent());
            Files.copy(in, targetConfig, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!migratedValues.isEmpty()) ConfigTextEditor.setValues(targetConfig, migratedValues);
        ConfigTextEditor.setScalar(targetConfig, "config-version", plugin.getDescription().getVersion() + "_auto_migration");
    }

    private static Map<String, String> legacyBaselines() {
        Map<String, String> out = new HashMap<>();
        out.put("4.5.5", "config-baselines/config-4.5.5.yml");
        out.put("4.6.0", "config-baselines/config-4.6.0.yml");
        out.put("4.6.1", "config-baselines/config-4.6.1.yml");
        out.put("4.6.2", "config-baselines/config-4.6.2.yml");
        out.put("4.6.3", "config-baselines/config-4.6.3.yml");
        out.put("4.6.4", "config-baselines/config-4.6.4.yml");
        out.put("4.7.0", "config-baselines/config-4.7.0.yml");
        return Map.copyOf(out);
    }

    private static YamlConfiguration loadPreviousDefaults(KokotoWebChatPlugin plugin, YamlConfiguration oldConfig) {
        String declared = String.valueOf(oldConfig.getString("config-version", "")).trim();
        String resource = LEGACY_BASELINES.get(declared);
        // Configs without a marker predate the marker in practice, so use the oldest
        // bundled BMWC baseline rather than treating every legacy default as custom.
        if ((resource == null || resource.isBlank()) && declared.isBlank()) {
            resource = LEGACY_BASELINES.get("4.5.5");
        }
        if (resource == null || resource.isBlank()) return new YamlConfiguration();
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new YamlConfiguration();
        }
    }

    private static boolean matchesPreviousDefault(YamlConfiguration previousDefaults, String oldPath, Object value) {
        if (previousDefaults == null || !previousDefaults.isSet(oldPath)) return false;
        return Objects.equals(normalizeComparable(previousDefaults.get(oldPath)), normalizeComparable(value));
    }

    private static Object normalizeComparable(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) out.put(key, normalizeComparable(section.get(key)));
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), normalizeComparable(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(normalizeComparable(item));
            return out;
        }
        return value;
    }

    private static boolean isKnownTargetPath(YamlConfiguration target, String path) {
        if (target.isSet(path) || target.isConfigurationSection(path)) return true;
        // Preserve user-defined entries under intentionally open maps such as
        // message-tokens.custom and emoji.game-link.aliases, while still dropping
        // retired normal settings that no longer exist in the 5.0.0 reference.
        int dot = path.lastIndexOf('.');
        while (dot > 0) {
            String parent = path.substring(0, dot);
            ConfigurationSection section = target.getConfigurationSection(parent);
            if (section != null) return section.getKeys(false).isEmpty();
            dot = parent.lastIndexOf('.');
        }
        return false;
    }

    private static Object migrateMappedValue(YamlConfiguration oldConfig, String oldPath, String mappedPath, Object legacyValue) {
        if ("standalone-web.path".equals(oldPath)) {
            String legacyPath = legacyValue == null ? "" : String.valueOf(legacyValue).trim();
            if ("/chat".equalsIgnoreCase(legacyPath) || "/bmwc/chat".equalsIgnoreCase(legacyPath)) return "/";
            return legacyValue;
        }
        if ("discordsrv.game-to-discord".equals(oldPath)) {
            boolean direct = legacyValue instanceof Boolean b
                    ? b
                    : Boolean.parseBoolean(String.valueOf(legacyValue));
            return direct ? "kwc" : "discordsrv";
        }
        // BMWC's documented same-origin HTTPS layout used /bmwc/api while the
        // standalone page was externally published at /bmwc/chat. KWC 5.0.0
        // separates the internal listener paths from the public reverse-proxy
        // prefix: internal standalone=/, internal API=/api, public prefix=/chat.
        // Normalize those standard BMWC public URL values back to KWC's empty
        // auto values instead of carrying /bmwc or hard-coding /chat/api.
        if (isLegacyStandardPublicUrlField(oldPath, mappedPath)) {
            if (isLegacyStandardPublicUrl(oldConfig, mappedPath, legacyValue)) return "";
            // A non-standard public URL implies an operator-managed proxy/domain.
            // Preserve it exactly; changing /bmwc inside that custom URL without
            // changing the external proxy would make the migrated config invalid.
            return legacyValue;
        }
        return migrateValue(legacyValue);
    }

    private static boolean isLegacyStandardPublicUrlField(String oldPath, String mappedPath) {
        return "web-addon.api-base-url".equals(oldPath)
                || "standalone-web.api-base-url".equals(oldPath)
                || "upload.public-base-url".equals(mappedPath)
                || "emoji.public-base-url".equals(mappedPath)
                || "emoji.game-link.public-api-base-url".equals(mappedPath);
    }

    private static boolean isLegacyStandardPublicUrl(YamlConfiguration oldConfig, String mappedPath, Object value) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        if (raw.isEmpty()) return true;
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        String suffix = switch (mappedPath) {
            case "upload.public-base-url" -> "/uploads";
            case "emoji.public-base-url" -> "/emojis";
            default -> "";
        };
        String expectedPath = "/bmwc/api" + suffix;
        if (lower.equals(expectedPath) || lower.equals(expectedPath.substring(1))) return true;
        // BMWC also allowed the shared /bmwc/api base in upload/emoji fields and
        // appended the resource suffix automatically.
        if (("upload.public-base-url".equals(mappedPath) || "emoji.public-base-url".equals(mappedPath))
                && (lower.equals("/bmwc/api") || lower.equals("bmwc/api"))) return true;

        String cors = oldConfig == null ? "" : String.valueOf(oldConfig.getString("http.cors-origin", "")).trim();
        if (!cors.isEmpty() && !"*".equals(cors)) {
            while (cors.endsWith("/")) cors = cors.substring(0, cors.length() - 1);
            if (lower.equals((cors + expectedPath).toLowerCase(java.util.Locale.ROOT))) return true;
            if (("upload.public-base-url".equals(mappedPath) || "emoji.public-base-url".equals(mappedPath))
                    && lower.equals((cors + "/bmwc/api").toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    private static void writeMarker(KokotoWebChatPlugin plugin, Path target) throws Exception {
        writeMarker(plugin, target,
                "Legacy import completed for KOKOTO WebChat " + plugin.getDescription().getVersion() + "\n");
    }

    private static void writeMarker(KokotoWebChatPlugin plugin, Path target, String message) throws Exception {
        Files.writeString(target.resolve(MARKER), message, StandardCharsets.UTF_8);
    }

    private static boolean hasExistingKwcData(Path target) throws Exception {
        if (!Files.isDirectory(target)) return false;
        try (var paths = Files.walk(target)) {
            return paths.anyMatch(path -> {
                if (path.equals(target)) return false;
                Path rel = target.relativize(path);
                if (rel.getNameCount() == 1 && MARKER.equals(rel.getFileName().toString())) return false;
                // Empty directories are not data. Any existing file/symlink is enough
                // to classify the directory as an established KWC installation.
                return !Files.isDirectory(path);
            });
        }
    }

    private static Path legacyDataDirectory(KokotoWebChatPlugin plugin) {
        File parent = plugin.getDataFolder().getParentFile();
        return parent == null ? null : parent.toPath().resolve(LEGACY_DATA_DIR);
    }

    private static void copyOperationalData(Path legacy, Path target) throws Exception {
        try (var paths = Files.walk(legacy)) {
            for (Path source : (Iterable<Path>) paths::iterator) {
                Path rel = legacy.relativize(source);
                if (rel.toString().isEmpty()) continue;
                if (skipLegacyGeneratedFile(rel)) continue;
                Path destination = target.resolve(rel.toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                    continue;
                }
                if (Files.exists(destination)) continue;
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                migrateCopiedLanguageFile(destination);
            }
        }
    }

    private static boolean skipLegacyGeneratedFile(Path rel) {
        String normalized = rel.toString().replace('\\', '/');
        String name = rel.getFileName() == null ? normalized : rel.getFileName().toString();
        if (normalized.equals("config.yml")) return true;
        if (name.startsWith("config-reference-") || name.startsWith("config-migration-") || name.startsWith("config-upgrade-")) return true;
        return name.equals("webapp.conf.example-additions") || name.equals(MARKER);
    }

    private static void migrateCopiedLanguageFile(Path destination) {
        String normalized = destination.toString().replace('\\', '/');
        if (!normalized.contains("/lang/") || !normalized.endsWith(".yml")) return;
        try {
            String text = Files.readString(destination, StandardCharsets.UTF_8);
            String migrated = text
                    .replace("BlueMapWebChat", "KOKOTO WebChat")
                    .replace("BM WebChat", "KOKOTO WebChat")
                    .replace("BlueMap Chat", "KOKOTO WebChat")
                    .replace("BlueMap 채팅", "KOKOTO WebChat")
                    .replace("BlueMap チャット", "KOKOTO WebChat")
                    .replace("BlueMap 聊天", "KOKOTO WebChat")
                    .replace("BMChat", "KWC")
                    .replace("/bmchat", "/kchat");
            if (!migrated.equals(text)) Files.writeString(destination, migrated, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static String mapPath(String path) {
        if (path == null || path.isBlank()) return "";
        if (path.equals("discordsrv.game-to-discord")) return "discordsrv.game-relay-mode";
        if (path.equals("discordsrv.game-to-discord-format")) return "discordsrv.game-relay-format";
        if (path.equals("ui.show-login-only-when-hidden")) return "";
        if (path.equals("web-addon")) return "adapters.bluemap";
        if (path.startsWith("web-addon.")) return "adapters.bluemap." + path.substring("web-addon.".length());
        if (path.equals("standalone-web")) return "frontend.standalone";
        if (path.startsWith("standalone-web.")) return "frontend.standalone." + path.substring("standalone-web.".length());
        return path;
    }

    private static Object migrateValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) out.put(key, migrateValue(section.get(key)));
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) out.put(String.valueOf(entry.getKey()), migrateValue(entry.getValue()));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(migrateValue(item));
            return out;
        }
        if (value instanceof String text) return migrateString(text);
        return value;
    }

    private static String migrateString(String value) {
        String out = value == null ? "" : value;
        if (out.equalsIgnoreCase("bluemapwebchat.admin")) return "kwc.admin";
        out = out.replace("plugins/BlueMapWebChat", "plugins/KOKOTO-WebChat");
        out = out.replace("addons/bluemap-web-chat", "addons/kokoto-web-chat");
        out = out.replace("/bmwc", "/chat");
        out = out.replace("bmwc/api", "chat/api");
        out = out.replace("/bmchat", "/kchat");
        out = out.replace("bluemapwebchat.", "kwc.");
        out = out.replace("BlueMapWebChat", "KOKOTO WebChat");
        out = out.replace("BM WebChat", "KOKOTO WebChat");
        out = out.replace("BMChat", "KWC");
        return out;
    }
}
