package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * PortableConfigMigration는 이전 KWC/BMWC 설치 데이터를 현재 형식으로 옮기는 migration 코드다.
 * PortableConfigMigration migrates older KWC/BMWC installation data into the current format.
 *
 * 기존 관리자 값을 가능한 한 보존하고, 한 번 적용한 migration을 재실행해도 결과가 달라지지 않는 idempotency를 유지해야 한다.
 * It should preserve administrator choices where possible and remain idempotent when the same migration is evaluated again.
 */
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
 * Migration reconstruction uses the bundled presentation template selected from
 * ui.language (English config.yml or the built-in KO/JA/ZH templates). Existing
 * operator values are overlaid on that fresh template. Canonical English defaults
 * remain the semantic comparison baseline; generated config-reference files are
 * administrator-readable presentation copies only and are never migration input.
 */
/**
 * KWC 유지보수 안내: 구 버전 config를 현재 canonical config로 올리면서 관리자 값을 최대한 보존하는 migration 엔진이다. baseline은 과거 버전별 기본값 비교에 사용하고, renamed/retired setting은 명시적으로 관리한다. migration은 원본 backup과 보고서를 남겨 자동 변경 내용을 추적할 수 있게 한다.
 *
 * KWC maintenance note: Migration engine upgrading older configs to the current canonical config while preserving administrator choices wherever possible. Historical baselines are used for default comparisons, while renamed/retired settings are explicit. Migration keeps backups and reports so automatic changes remain auditable.
 */
public final class PortableConfigMigration {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final java.util.Set<String> RETIRED_SETTINGS = java.util.Set.of(
            "ui.show-login-only-when-hidden",
            "ui.virtual-scroll.preserve-visible-media",
            "ui.virtual-scroll.preserve-playing-media",
            "ui.resume-refresh.skip-while-media-active",
            "notifications.notify-own-messages",
            "notifications.show-message-preview",
            "server-relay.forward-received-public-chat",
            "direct-message.confirm-hide",
            "group-chat.confirm-hide"
    );
    private static final Map<String,String> RENAMED_SETTINGS = Map.of(
            "direct-message.confirm-hide", "direct-message.confirm-delete",
            "group-chat.confirm-hide", "group-chat.confirm-delete"
    );
    private static final String CONFIG_LANGUAGE_MARKER = "# KWC config-comment-language: ";
    private static final Map<String, String> CONFIG_TEMPLATE_RESOURCES = Map.of(
            "ko-KR", "config-templates/config-ko-KR.yml",
            "ja-JP", "config-templates/config-ja-JP.yml",
            "zh-CN", "config-templates/config-zh-CN.yml"
    );

    private static final Map<String, String> BASELINE_RESOURCES = Map.ofEntries(
            Map.entry("4.5.5", "config-baselines/config-4.5.5.yml"),
            Map.entry("4.6.0", "config-baselines/config-4.6.0.yml"),
            Map.entry("4.6.1", "config-baselines/config-4.6.1.yml"),
            Map.entry("4.6.2", "config-baselines/config-4.6.2.yml"),
            Map.entry("4.6.3", "config-baselines/config-4.6.3.yml"),
            Map.entry("4.6.4", "config-baselines/config-4.6.4.yml"),
            Map.entry("4.7.0", "config-baselines/config-4.7.0.yml"),
            Map.entry("5.0.0", "config-baselines/config-5.0.0.yml"),
            Map.entry("5.1.0", "config-baselines/config-5.1.0.yml"),
            Map.entry("5.2.0", "config-baselines/config-5.2.0.yml"),
            Map.entry("5.3.0", "config-baselines/config-5.3.0.yml")
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

    // 현재 config와 과거 baseline을 비교해 사용자 수정값을 식별하고 새 canonical template에 재적용한다. renamed/retired setting, 주석 언어, backup/report 생성까지 한 번의 migration 흐름에서 처리한다.
    // Compares the current config with its historical baseline, identifies administrator overrides, and reapplies them onto the new canonical template. Renamed/retired settings, comment language, backup, and report generation are handled in the same migration flow.
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

        // Parse the operator config before selecting a presentation template. ui.language
        // controls comments/layout/reference/report language; the parsed setting values are
        // always overlaid back onto the selected template and therefore remain authoritative.
        Map<String,Object> actual = loadSnapshot(loader, Files.newInputStream(configPath));
        // 5.3.0 RC31/RC32 added asymmetric per-peer Relay policy inside the groups YAML list.
        // A flat canonical default cannot express keys for an operator-created peer, so ordinary
        // missing-setting comparison cannot insert these nested values. Augment existing peer maps
        // explicitly while preserving scalar send/receive shortcuts and every operator value.
        int relayPeerPolicyInsertions = augmentRelayPeerPolicies(actual);
        String declared = safe(string(actual.get("config-version")));
        String configLanguage = normalizeConfigLanguage(actual.get("ui.language"));
        String templateResource = configTemplateResource(configLanguage);
        byte[] templateBytes = resourceBytes(opener, templateResource);
        String templateText = new String(templateBytes, StandardCharsets.UTF_8);
        writeReference(referencePath, templateBytes);

        // Defaults are compared from the canonical English config. Every localized template
        // is required by validation to contain the exact same parsed values.
        Map<String,Object> currentDefaults = loadSnapshot(loader, opener.open("config.yml"));
        String beforeText = Files.readString(configPath, StandardCharsets.UTF_8);
        String renderedLanguage = detectConfigCommentLanguage(beforeText);

        if (target.equals(declared)) {
            boolean languageChanged = !configLanguage.equals(renderedLanguage);
            boolean retiredPresent = actual.keySet().stream().anyMatch(RETIRED_SETTINGS::contains);
            boolean relayPeerPolicyChanged = relayPeerPolicyInsertions > 0;
            if (languageChanged || retiredPresent || relayPeerPolicyChanged) {
                rebuildPhysical(configPath, templateText, actual, target, false);
            }
            boolean removed = Files.deleteIfExists(reportPath) | Files.deleteIfExists(legacyGuidePath);
            if (info != null) {
                String prefix = "Config version " + target + " has automatic migration disabled.";
                if (languageChanged || retiredPresent || relayPeerPolicyChanged) {
                    List<String> reasons = new ArrayList<>();
                    if (languageChanged) reasons.add("ui.language/comment layout");
                    if (retiredPresent) reasons.add("retired settings");
                    if (relayPeerPolicyChanged) reasons.add("missing Relay peer send/receive policy");
                    info.log(prefix + " Rebuilt the current template for " + String.join(", ", reasons)
                            + " while preserving active parsed setting values; Relay peer policy values inserted="
                            + relayPeerPolicyInsertions + ".");
                } else {
                    info.log(removed
                            ? prefix + " Stale migration files were removed."
                            : prefix + " Migration comparison was skipped.");
                }
            }
            return new Result(languageChanged || retiredPresent || relayPeerPolicyChanged, false, relayPeerPolicyInsertions);
        }

        if (autoVersion.equals(declared)) {
            MigrationDiff before = compare(actual, currentDefaults, currentDefaults);
            List<String> newlyInserted = new ArrayList<>(before.missingSettings.keySet());
            rebuildPhysical(configPath, templateText, actual, autoVersion, false);
            String afterText = Files.readString(configPath, StandardCharsets.UTF_8);
            Map<String,Object> refreshed = loadSnapshot(loader, Files.newInputStream(configPath));
            MigrationDiff current = compare(refreshed, currentDefaults, currentDefaults);
            writeReport(reportPath, configPath, referencePath, target, autoVersion,
                    target + " (same-version automatic migration)", current, newlyInserted.size(),
                    refreshed, currentDefaults, configLanguage);
            Files.deleteIfExists(legacyGuidePath);
            if (info != null) info.log("Config automatic migration is enabled: config-version=\"" + autoVersion
                    + "\". Rebuilt from the " + configLanguage + " bundled config template and overlaid existing values; newly inserted settings=" + (newlyInserted.size() + relayPeerPolicyInsertions)
                    + ". Existing configured values were preserved. Set config-version to \"" + target
                    + "\" only if same-version automatic migration should be disabled.");
            return new Result(!afterText.equals(beforeText), true, newlyInserted.size() + relayPeerPolicyInsertions);
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
        boolean resetRelayV2 = versionAtLeast(target, 5, 1, 0) && !versionAtLeast(declared, 5, 1, 0);
        rebuildPhysical(configPath, templateText, actual, autoVersion, resetRelayV2);
        Map<String,Object> migrated = loadSnapshot(loader, Files.newInputStream(configPath));
        if (!autoVersion.equals(safe(string(migrated.get("config-version"))))) {
            throw new IllegalStateException("migration marker was not persisted");
        }
        MigrationDiff after = compare(migrated, previousDefaults, currentDefaults);
        Files.deleteIfExists(legacyGuidePath);
        writeReport(reportPath, configPath, referencePath, target, declared, baselineVersion, after,
                before.missingSettings.size(), migrated, currentDefaults, configLanguage);
        if (info != null) info.log("Config migration completed: detected=" + (declared.isBlank() ? "not set" : declared)
                + ", target=" + target + ", baseline=" + (baselineVersion.isBlank() ? "not available" : baselineVersion)
                + ", template-language=" + configLanguage
                + ", auto-inserted=" + (before.missingSettings.size() + relayPeerPolicyInsertions) + ", changed-default-review=" + after.changedDefaults.size()
                + "; leave config-version as \"" + autoVersion + "\" to keep automatic migration, or set it to \""
                + target + "\" to disable same-version automatic migration.");
        return new Result(true, true, before.missingSettings.size() + relayPeerPolicyInsertions);
    }

    private static int augmentRelayPeerPolicies(Map<String,Object> actual) {
        if (actual == null) return 0;
        Object rawGroups = actual.get("server-relay.groups");
        if (!(rawGroups instanceof List<?> groups) || groups.isEmpty()) return 0;

        ArrayList<Object> rebuiltGroups = new ArrayList<>(groups.size());
        int inserted = 0;
        boolean changed = false;
        for (Object rawGroup : groups) {
            if (!(rawGroup instanceof Map<?,?> groupMap)) {
                rebuiltGroups.add(rawGroup);
                continue;
            }
            LinkedHashMap<String,Object> group = stringKeyMap(groupMap);
            Object rawPeers = group.get("peers");
            if (!(rawPeers instanceof List<?> peers) || peers.isEmpty()) {
                rebuiltGroups.add(group);
                continue;
            }
            ArrayList<Object> rebuiltPeers = new ArrayList<>(peers.size());
            boolean groupChanged = false;
            for (Object rawPeer : peers) {
                if (!(rawPeer instanceof Map<?,?> peerMap)) {
                    rebuiltPeers.add(rawPeer);
                    continue;
                }
                LinkedHashMap<String,Object> peer = stringKeyMap(peerMap);
                int before = inserted;
                inserted += augmentRelayDirection(peer, "send");
                inserted += augmentRelayDirection(peer, "receive");
                if (inserted != before) groupChanged = true;
                rebuiltPeers.add(peer);
            }
            if (groupChanged) {
                group.put("peers", rebuiltPeers);
                changed = true;
            }
            rebuiltGroups.add(group);
        }
        if (changed) actual.put("server-relay.groups", rebuiltGroups);
        return inserted;
    }

    private static int augmentRelayDirection(LinkedHashMap<String,Object> peer, String direction) {
        Object raw = peer.get(direction);
        if (raw != null && !(raw instanceof Map<?,?>)) {
            // Boolean/scalar shorthand is intentional and controls the entire direction.
            return 0;
        }
        LinkedHashMap<String,Object> policy = raw instanceof Map<?,?> map ? stringKeyMap(map) : new LinkedHashMap<>();
        int inserted = 0;
        for (String key : List.of("public-chat", "event", "dm", "profile")) {
            if (!policy.containsKey(key)) {
                policy.put(key, true);
                inserted++;
            }
        }
        if (raw == null || inserted > 0) peer.put(direction, policy);
        return inserted;
    }

    private static LinkedHashMap<String,Object> stringKeyMap(Map<?,?> source) {
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        for (Map.Entry<?,?> entry : source.entrySet()) out.put(String.valueOf(entry.getKey()), entry.getValue());
        return out;
    }

    private static void rebuildPhysical(Path configPath,
                                        String templateText,
                                        Map<String,Object> actual,
                                        String resultingVersion,
                                        boolean resetRelayV2) throws Exception {
        // Reconstruct comments/layout from the selected current template. Existing parsed
        // operator values are the overlay and remain authoritative across language changes.
        Files.writeString(configPath, templateText, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        LinkedHashMap<String,Object> overlay = new LinkedHashMap<>();
        if (actual != null) overlay.putAll(actual);
        overlay.remove("config-version");
        for (Map.Entry<String,String> rename : RENAMED_SETTINGS.entrySet()) {
            Object oldValue = overlay.get(rename.getKey());
            if (oldValue != null && !overlay.containsKey(rename.getValue())) overlay.put(rename.getValue(), oldValue);
        }
        RETIRED_SETTINGS.forEach(overlay::remove);
        if (resetRelayV2) {
            // Relay v1 and relay v2 do not share a safely inferable trust topology.
            // Do not guess groups from old flat peers/secrets: force relay off and let
            // the operator explicitly define v2 groups before enabling it again.
            overlay.remove("server-relay.enabled");
            overlay.remove("server-relay.shared-secret");
            overlay.remove("server-relay.peers");
            overlay.remove("server-relay.forwarding.enabled");
            overlay.remove("server-relay.forwarding");
            overlay.remove("server-relay.groups");
            overlay.keySet().removeIf(path -> path.startsWith("server-relay.groups."));
        }
        if (!overlay.isEmpty()) ConfigTextEditor.setValues(configPath, overlay);
        ConfigTextEditor.setScalar(configPath, "config-version", resultingVersion);
    }

    private static String configTemplateResource(String language) {
        return CONFIG_TEMPLATE_RESOURCES.getOrDefault(normalizeConfigLanguage(language), "config.yml");
    }

    private static String normalizeConfigLanguage(Object value) {
        String raw = safe(string(value)).replace('_', '-').toLowerCase();
        if (raw.equals("ko") || raw.equals("ko-kr")) return "ko-KR";
        if (raw.equals("ja") || raw.equals("ja-jp")) return "ja-JP";
        if (raw.equals("zh") || raw.equals("zh-cn") || raw.equals("zh-hans") || raw.equals("zh-hans-cn")) return "zh-CN";
        return "en-US";
    }

    private static String detectConfigCommentLanguage(String text) {
        if (text != null) {
            int checked = 0;
            for (String line : text.split("\\R", -1)) {
                if (++checked > 80) break;
                String trimmed = line.trim();
                if (!trimmed.startsWith(CONFIG_LANGUAGE_MARKER)) continue;
                return normalizeConfigLanguage(trimmed.substring(CONFIG_LANGUAGE_MARKER.length()));
            }
        }
        // All 5.1.0 configs before localized templates were introduced used English comments.
        return "en-US";
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

    private static boolean versionAtLeast(String value, int major, int minor, int patch) {
        String raw = safe(value);
        int suffix = raw.indexOf('_');
        if (suffix >= 0) raw = raw.substring(0, suffix);
        String[] parts = raw.split("\\.");
        if (parts.length < 3) return false;
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            int c = Integer.parseInt(parts[2]);
            if (a != major) return a > major;
            if (b != minor) return b > minor;
            return c >= patch;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
                                    Path configPath,
                                    Path referencePath,
                                    String targetVersion,
                                    String declaredVersion,
                                    String baselineVersion,
                                    MigrationDiff diff,
                                    int autoInsertedSettings,
                                    Map<String,Object> currentValues,
                                    Map<String,Object> bundledDefaults,
                                    String language) throws Exception {
        String lang = normalizeConfigLanguage(language);
        String detected = safe(declaredVersion).isBlank()
                ? localized(lang, "not set", "설정되지 않음", "未設定", "未设置")
                : safe(declaredVersion);
        String baseline = safe(baselineVersion).isBlank()
                ? localized(lang, "not available", "사용할 수 없음", "利用不可", "不可用")
                : safe(baselineVersion);
        StringBuilder header = new StringBuilder();
        header.append(localized(lang,
                "# KOKOTO WebChat configuration migration report\n",
                "# KOKOTO WebChat 설정 마이그레이션 보고서\n",
                "# KOKOTO WebChat 設定マイグレーションレポート\n",
                "# KOKOTO WebChat 配置迁移报告\n"));
        header.append(localized(lang,
                "# Migration reconstructs config.yml from the current language template and overlays the existing parsed setting values.\n",
                "# 마이그레이션은 현재 언어 템플릿으로 config.yml의 주석/레이아웃을 재구성한 뒤 기존에 파싱된 설정값을 덮어씁니다.\n",
                "# マイグレーションは現在の言語テンプレートで config.yml のコメント/レイアウトを再構成し、既存の解析済み設定値を上書きします。\n",
                "# 迁移会使用当前语言模板重建 config.yml 的注释/布局，再覆盖现有已解析的设置值。\n"));
        header.append(localized(lang,
                "# Existing configured values are preserved; comments/order/whitespace/indentation come from the selected current template.\n",
                "# 기존 설정값은 보존되며 주석/순서/공백/들여쓰기는 선택된 최신 언어 템플릿을 따릅니다.\n",
                "# 既存の設定値は保持され、コメント/順序/空白/インデントは選択された最新言語テンプレートに従います。\n",
                "# 现有设置值会被保留；注释/顺序/空白/缩进来自所选的当前语言模板。\n"));
        header.append(localized(lang,
                "# config-reference-<version>.yml is the administrator-readable current default in the same ui.language; it is not migration input.\n",
                "# config-reference-<version>.yml은 ui.language와 같은 언어의 관리자용 최신 기본 설정 사본이며 마이그레이션 입력으로 사용하지 않습니다.\n",
                "# config-reference-<version>.yml は ui.language と同じ言語の管理者向け最新デフォルト設定であり、マイグレーション入力には使用しません。\n",
                "# config-reference-<version>.yml 是与 ui.language 相同语言的管理员可读当前默认配置，不作为迁移输入。\n"));
        header.append(localized(lang,
                "# The migrated config is marked <version>_auto_migration; while present, startup/reload repeats the current-template rebuild with values overlaid.\n",
                "# 마이그레이션된 config는 <version>_auto_migration으로 표시되며, 이 값이 유지되는 동안 시작/리로드 때 최신 템플릿 재구성과 값 보존을 반복합니다.\n",
                "# 移行後の config は <version>_auto_migration と表示され、この値がある間は起動/リロード時に最新テンプレート再構成と値の上書きを繰り返します。\n",
                "# 迁移后的 config 标记为 <version>_auto_migration；保留该标记时，每次启动/重载都会用当前模板重建并覆盖保留值。\n"));
        header.append(localized(lang,
                "# This marker controls automatic migration; it is not a review-status marker. Set config-version to the exact plugin version to disable same-version automatic migration.\n",
                "# 이 표시는 자동 마이그레이션 동작을 제어하며 검토 완료 여부 표시는 아닙니다. 같은 버전 자동 재구성을 끄려면 config-version을 정확한 플러그인 버전으로 설정합니다.\n",
                "# このマーカーは自動マイグレーションを制御するもので、レビュー状態ではありません。同一バージョンの自動再構成を止めるには config-version を正確なプラグインバージョンにします。\n",
                "# 此标记控制自动迁移，并非审核状态。要禁用同版本自动重建，请将 config-version 设置为精确的插件版本。\n"));
        header.append(localized(lang,
                "# The setting-value comparison below is semantic: comments, layout, quotes, line positions and key order do not affect Difference results.\n",
                "# 아래 설정값 비교는 의미 기반입니다. 주석, 레이아웃, 따옴표, 줄 위치, 키 순서는 Difference 판정에 영향을 주지 않습니다.\n",
                "# 以下の設定値比較は意味ベースです。コメント、レイアウト、引用符、行位置、キー順は Difference 判定に影響しません。\n",
                "# 下方设置值比较采用语义判定；注释、布局、引号、行位置和键顺序不会影响 Difference 结果。\n"));
        header.append(localized(lang, "# Detected config version: ", "# 감지된 config 버전: ", "# 検出した config バージョン: ", "# 检测到的 config 版本: ")).append(commentValue(detected)).append("\n");
        header.append(localized(lang, "# Target plugin version: ", "# 대상 플러그인 버전: ", "# 対象プラグインバージョン: ", "# 目标插件版本: ")).append(commentValue(targetVersion)).append("\n");
        header.append(localized(lang, "# Comparison baseline: ", "# 비교 기준: ", "# 比較基準: ", "# 比较基线: ")).append(commentValue(baseline)).append("\n");
        header.append(localized(lang, "# Config comment language: ", "# 설정 주석 언어: ", "# 設定コメント言語: ", "# 配置注释语言: ")).append(lang).append("\n");
        header.append(localized(lang, "# Settings supplied by the new bundled default in this migration: ", "# 이번 마이그레이션에서 새 기본값이 추가한 설정 수: ", "# 今回のマイグレーションで新しいデフォルトから追加された設定数: ", "# 本次迁移由新默认配置补充的设置数: ")).append(autoInsertedSettings).append("\n");
        header.append(localized(lang, "# Missing settings still unresolved: ", "# 아직 해결되지 않은 누락 설정 수: ", "# 未解決の不足設定数: ", "# 仍未解决的缺失设置数: ")).append(diff.missingSettings.size()).append("\n");
        header.append(localized(lang, "# Changed defaults requiring manual review: ", "# 수동 확인이 필요한 기본값 변경 수: ", "# 手動確認が必要なデフォルト変更数: ", "# 需要手动确认的默认值变更数: ")).append(diff.changedDefaults.size()).append("\n");
        header.append(localized(lang, "# Automatic migration: ENABLED\n", "# 자동 마이그레이션: 활성화\n", "# 自動マイグレーション: 有効\n", "# 自动迁移: 已启用\n"));
        if (!diff.changedDefaults.isEmpty()) {
            header.append(localized(lang,
                    "#\n# Changed bundled defaults included in the fragment:\n",
                    "#\n# 아래 조각에 포함된 변경된 번들 기본값:\n",
                    "#\n# 下のフラグメントに含まれる変更済みバンドルデフォルト:\n",
                    "#\n# 下方片段中包含的已变更内置默认值:\n"));
            for (Map<String,Object> item : diff.changedDefaults) {
                header.append("# - ").append(commentValue(item.get("path"))).append("\n");
                header.append(localized(lang, "#   configured and previous default: ", "#   현재 설정값 및 이전 기본값: ", "#   現在の設定値および以前のデフォルト: ", "#   当前设置值及旧默认值: ")).append(commentValue(item.get("previous-default"))).append("\n");
                header.append(localized(lang, "#   new bundled default: ", "#   새 번들 기본값: ", "#   新しいバンドルデフォルト: ", "#   新内置默认值: ")).append(commentValue(item.get("current-default"))).append("\n");
            }
        }
        if (!diff.missingSettings.isEmpty()) {
            header.append(localized(lang,
                    "#\n# Missing settings still unresolved (normally empty after automatic insertion):\n",
                    "#\n# 아직 해결되지 않은 누락 설정(자동 삽입 후에는 보통 비어 있어야 함):\n",
                    "#\n# 未解決の不足設定（自動挿入後は通常空です）:\n",
                    "#\n# 仍未解决的缺失设置（自动补充后通常应为空）:\n"));
            for (String path : diff.missingSettings.keySet()) header.append("# - ").append(commentValue(path)).append("\n");
        }
        header.append(localized(lang,
                "#\n# The config-version entry below is the automatic-migration stop marker. Use the exact version only to disable further same-version automatic migration.\n",
                "#\n# 아래 config-version 항목은 자동 마이그레이션 중지 표시입니다. 같은 버전 자동 마이그레이션을 끌 때만 정확한 버전값을 사용하세요.\n",
                "#\n# 下の config-version は自動マイグレーション停止マーカーです。同一バージョンの自動マイグレーションを止める場合だけ正確なバージョンを使用してください。\n",
                "#\n# 下方 config-version 是自动迁移停止标记。仅在要禁用后续同版本自动迁移时使用精确版本值。\n"));
        header.append(localized(lang, "# Otherwise leave config.yml as ", "# 그 외에는 config.yml을 ", "# それ以外は config.yml を ", "# 否则请将 config.yml 保持为 "))
                .append(targetVersion).append("_auto_migration.\n\n");

        String fragment = reportFragment(diff.changedDefaults, targetVersion);
        String body = header + fragment;
        if (!body.endsWith("\n")) body += "\n";
        body += "\n" + buildSemanticSettingDiff(configPath, referencePath, currentValues, bundledDefaults, lang);
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
                                                   Map<String,Object> bundledDefaults,
                                                   String language) {
        String lang = normalizeConfigLanguage(language);
        Map<String,Object> current = normalizeFlat(currentValues == null ? Map.of() : currentValues);
        Map<String,Object> defaults = normalizeFlat(bundledDefaults == null ? Map.of() : bundledDefaults);
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.addAll(defaults.keySet());
        for (String path : current.keySet()) if (!paths.contains(path)) paths.add(path);

        ArrayList<String> changedPaths = new ArrayList<>();
        for (String path : paths) {
            // config-version is the migration mode/stop marker, not an operator setting
            // difference. Excluding it keeps the report focused on actual configured values.
            if ("config-version".equals(path) || RETIRED_SETTINGS.contains(path)) continue;
            boolean inCurrent = current.containsKey(path);
            boolean inDefault = defaults.containsKey(path);
            if (inCurrent != inDefault || !valuesEqual(current.get(path), defaults.get(path))) changedPaths.add(path);
        }

        String referenceName = commentValue(referencePath.getFileName());
        StringBuilder out = new StringBuilder();
        out.append("# =============================================================================\n");
        out.append(localized(lang,
                "# " + referenceName + " -> current config.yml parsed setting diff\n",
                "# " + referenceName + " → 현재 config.yml 파싱 설정값 차이\n",
                "# " + referenceName + " → 現在の config.yml 解析済み設定値差分\n",
                "# " + referenceName + " → 当前 config.yml 已解析设置值差异\n"));
        out.append("# =============================================================================\n");
        out.append(localized(lang,
                "# Comparison uses parsed YAML path/value pairs only. Comments, blank lines, indentation, quoting, line positions and key order are ignored.\n",
                "# 비교에는 파싱된 YAML path/value만 사용합니다. 주석, 빈 줄, 들여쓰기, 따옴표, 줄 위치, 키 순서는 무시합니다.\n",
                "# 比較には解析済み YAML の path/value だけを使用します。コメント、空行、インデント、引用符、行位置、キー順は無視します。\n",
                "# 比较仅使用已解析的 YAML path/value。注释、空行、缩进、引号、行位置和键顺序都会被忽略。\n"));
        out.append(localized(lang,
                "# Each difference shows only the YAML value block for that setting. Explanatory comments stay in config.yml/reference and are not duplicated here. Structured list/map settings stay multi-line YAML.\n",
                "# 각 차이는 해당 설정의 YAML 값 블록만 표시합니다. 설명 주석은 config.yml/reference에 그대로 두고 여기에는 중복하지 않습니다. list/map 구조는 여러 줄 YAML을 유지합니다.\n",
                "# 各差分にはその設定の YAML 値ブロックだけを表示します。説明コメントは config.yml/reference 側に残し、ここでは重複表示しません。list/map 構造は複数行 YAML を維持します。\n",
                "# 每个差异只显示该设置的 YAML 值块。说明注释保留在 config.yml/reference 中，不在此重复；list/map 结构保持多行 YAML。\n"));
        if (changedPaths.isEmpty()) return out.append(localized(lang,
                "# No setting-value differences found.\n",
                "# 설정값 차이가 없습니다.\n",
                "# 設定値の差異はありません。\n",
                "# 未发现设置值差异。\n")).toString();

        Map<String, ConfigTextEditor.SettingBlock> currentBlocks;
        Map<String, ConfigTextEditor.SettingBlock> defaultBlocks;
        try {
            currentBlocks = ConfigTextEditor.readSettingBlocks(configPath, changedPaths);
        } catch (Exception ignored) {
            currentBlocks = Map.of();
        }
        try {
            defaultBlocks = ConfigTextEditor.readSettingBlocks(referencePath, changedPaths);
        } catch (Exception ignored) {
            defaultBlocks = Map.of();
        }

        int block = 0;
        for (String path : changedPaths) {
            block++;
            out.append(localized(lang, "# Difference ", "# 차이 ", "# 差分 ", "# 差异 ")).append(block).append("\n");
            out.append(localized(lang, "# Setting: ", "# 설정: ", "# 設定: ", "# 设置: ")).append(commentValue(path)).append("\n#\n");
            if (defaults.containsKey(path)) {
                out.append("# - ").append(referenceName).append("\n");
                appendCommentedYamlValueBlock(out, defaultBlocks.get(path), defaults.get(path));
            } else {
                out.append("# - ").append(referenceName).append("\n").append(localized(lang,
                        "# <not present in bundled defaults; operator/custom setting>\n",
                        "# <번들 기본값에 없음; 운영자/사용자 정의 설정>\n",
                        "# <バンドルデフォルトに存在しない運用者/カスタム設定>\n",
                        "# <内置默认配置中不存在；管理员/自定义设置>\n"));
            }
            out.append("#\n");
            if (current.containsKey(path)) {
                out.append("# + config.yml\n");
                appendCommentedYamlValueBlock(out, currentBlocks.get(path), current.get(path));
            } else {
                out.append("# + config.yml\n").append(localized(lang, "# <missing>\n", "# <누락>\n", "# <不足>\n", "# <缺失>\n"));
            }
            out.append("#\n#\n#\n");
        }
        return out.toString();
    }

    private static void appendCommentedYamlValueBlock(StringBuilder out, ConfigTextEditor.SettingBlock block, Object fallbackValue) {
        if (block != null && block.lines() != null && !block.lines().isEmpty()) {
            boolean emitted = false;
            for (String line : block.lines()) {
                if (line == null) continue;
                String trimmed = line.trim();
                // The migration report is a value diff, not a second copy of the
                // configuration manual. Attached explanatory comments remain in the
                // real config/reference files and are intentionally omitted here.
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                out.append("# ").append(line).append("\n");
                emitted = true;
            }
            if (emitted) return;
        }
        // Fallback is used only when the physical value block cannot be located. Keep
        // structured values readable even then instead of reverting to one-line JSON.
        for (String line : yamlValueLines(fallbackValue)) out.append("# ").append(line).append("\n");
    }

    private static List<String> yamlValueLines(Object value) {
        Object normalized = normalizeValue(value);
        if (normalized == null || normalized instanceof Boolean || normalized instanceof Number || normalized instanceof String) {
            return List.of(serializedValue(normalized));
        }
        ArrayList<String> lines = new ArrayList<>();
        renderYamlValue(lines, 0, normalized);
        return lines.isEmpty() ? List.of(serializedValue(normalized)) : lines;
    }

    private static void renderYamlValue(List<String> out, int indent, Object value) {
        String prefix = " ".repeat(Math.max(0, indent));
        if (value instanceof List<?> list) {
            if (list.isEmpty()) { out.add(prefix + "[]"); return; }
            for (Object item : list) {
                if (item instanceof Map<?,?> map) {
                    if (map.isEmpty()) { out.add(prefix + "- {}"); continue; }
                    boolean first = true;
                    for (Map.Entry<?,?> entry : map.entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        Object child = entry.getValue();
                        String head = first ? prefix + "- " : prefix + "  ";
                        if (child instanceof Map<?,?> || child instanceof List<?>) {
                            if ((child instanceof Map<?,?> m && m.isEmpty()) || (child instanceof List<?> l && l.isEmpty())) {
                                out.add(head + key + ": " + serializedValue(child));
                            } else {
                                out.add(head + key + ":");
                                renderYamlValue(out, indent + 4, child);
                            }
                        } else {
                            out.add(head + key + ": " + serializedValue(child));
                        }
                        first = false;
                    }
                } else if (item instanceof List<?>) {
                    out.add(prefix + "-");
                    renderYamlValue(out, indent + 2, item);
                } else {
                    out.add(prefix + "- " + serializedValue(item));
                }
            }
            return;
        }
        if (value instanceof Map<?,?> map) {
            if (map.isEmpty()) { out.add(prefix + "{}"); return; }
            for (Map.Entry<?,?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object child = entry.getValue();
                if (child instanceof Map<?,?> || child instanceof List<?>) {
                    if ((child instanceof Map<?,?> m && m.isEmpty()) || (child instanceof List<?> l && l.isEmpty())) {
                        out.add(prefix + key + ": " + serializedValue(child));
                    } else {
                        out.add(prefix + key + ":");
                        renderYamlValue(out, indent + 2, child);
                    }
                } else {
                    out.add(prefix + key + ": " + serializedValue(child));
                }
            }
            return;
        }
        out.add(prefix + serializedValue(value));
    }

    private static String serializedValue(Object value) {
        Object normalized = normalizeValue(value);
        if (normalized == null) return "null";
        if (normalized instanceof Boolean || normalized instanceof Number) return String.valueOf(normalized);
        if (normalized instanceof String text) return quoteFlowString(text);
        if (normalized instanceof List<?> list) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(serializedValue(list.get(i)));
            }
            return out.append(']').toString();
        }
        if (normalized instanceof Map<?,?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?,?> entry : map.entrySet()) {
                if (!first) out.append(", ");
                first = false;
                out.append(quoteFlowString(String.valueOf(entry.getKey()))).append(": ").append(serializedValue(entry.getValue()));
            }
            return out.append('}').toString();
        }
        return quoteFlowString(String.valueOf(normalized));
    }

    private static String quoteFlowString(String value) {
        String text = String.valueOf(value == null ? "" : value);
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int)ch));
                    else out.append(ch);
                }
            }
        }
        return out.append('"').toString();
    }

    private static String localized(String language, String en, String ko, String ja, String zh) {
        return switch (normalizeConfigLanguage(language)) {
            case "ko-KR" -> ko;
            case "ja-JP" -> ja;
            case "zh-CN" -> zh;
            default -> en;
        };
    }

    private record MigrationDiff(LinkedHashMap<String,Object> missingSettings,List<Map<String,Object>> changedDefaults){}

    private static String commentValue(Object value) {
        String text=String.valueOf(value==null?"null":value).replace('\r',' ').replace('\n',' ').trim();
        return text.length()>240 ? text.substring(0,237)+"..." : text;
    }
    private static String string(Object value){ return value==null?"":String.valueOf(value); }
    private static String safe(String value){ return value==null?"":value.trim(); }
}
