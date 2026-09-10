/* KWC 파일 안내 / KWC file guide
 * PortableConfigMigrationHarness는 이전 KWC/BMWC 설치 데이터를 현재 형식으로 옮기는 migration 코드다.
 * PortableConfigMigrationHarness migrates older KWC/BMWC installation data into the current format.
 *
 * 기존 관리자 값을 가능한 한 보존하고, 한 번 적용한 migration을 재실행해도 결과가 달라지지 않는 idempotency를 유지해야 한다.
 * It should preserve administrator choices where possible and remain idempotent when the same migration is evaluated again.
 */
import dev.kokoto.webchat.PortableConfigMigration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Representative file-I/O regression harness for the loader-neutral 5.3.0 config migration engine. */
public final class PortableConfigMigrationHarness {
    private static int checks;

    private static final String CURRENT_EN = """
            # KWC config-comment-language: en-US
            config-version: "5.3.0"
            ui:
              language: "en-US"
            feature:
              enabled: true
              label: "current-default"
            new-section:
              inserted-setting: 42
            chat:
              conversation-archive:
                enabled: true
                max-archives-per-user: 100
                max-messages-per-archive: 1000
                max-messages-per-user: 10000
              typing-indicator:
                open-chat:
                  enabled: false
                dm:
                  enabled: true
                group-chat:
                  enabled: true
            direct-message:
              confirm-delete: true
            group-chat:
              confirm-delete: true
            notifications:
              notify-reactions: true
            server-relay:
              enabled: false
              groups: []
            """;

    private static final String CURRENT_KO = """
            # KWC config-comment-language: ko-KR
            # 한국어 구성 주석 템플릿
            config-version: "5.3.0"
            ui:
              language: "ko-KR"
            feature:
              enabled: true
              label: "current-default"
            new-section:
              inserted-setting: 42
            chat:
              conversation-archive:
                enabled: true
                max-archives-per-user: 100
                max-messages-per-archive: 1000
                max-messages-per-user: 10000
              typing-indicator:
                open-chat:
                  enabled: false
                dm:
                  enabled: true
                group-chat:
                  enabled: true
            direct-message:
              confirm-delete: true
            group-chat:
              confirm-delete: true
            notifications:
              notify-reactions: true
            server-relay:
              enabled: false
              groups: []
            """;

    private static final String BASE_520 = """
            # KWC config-comment-language: en-US
            config-version: "5.2.0"
            ui:
              language: "en-US"
            feature:
              enabled: true
              label: "current-default"
            direct-message:
              confirm-hide: true
            group-chat:
              confirm-hide: true
            notifications:
              notify-reactions: true
            server-relay:
              enabled: true
              groups: []
            """;

    private static final String BASE_510 = """
            # KWC config-comment-language: en-US
            config-version: "5.1.0"
            ui:
              language: "en-US"
            feature:
              enabled: true
              label: "current-default"
            new-section:
              inserted-setting: 42
            server-relay:
              enabled: false
              groups: []
            """;

    private static final String BASE_500 = """
            # KWC config-comment-language: en-US
            config-version: "5.0.0"
            ui:
              language: "en-US"
            feature:
              enabled: true
              label: "old-default"
            server-relay:
              enabled: true
              shared-secret: "legacy-default"
            """;

    private static final String BASE_455 = """
            # KWC config-comment-language: en-US
            config-version: "4.5.5"
            ui:
              language: "en-US"
            feature:
              enabled: true
            """;

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("kwc-config-migration-harness-");
        try {
            fixedCurrentSkipsSameVersion(temp.resolve("fixed"));
            fixedCurrentRemovesRetiredPreview(temp.resolve("fixed-retired-preview"));
            fixedCurrentRenamesMessageDeleteConfirmations(temp.resolve("fixed-delete-confirm"));
            languageChangeRebuildsWithoutAutoMigration(temp.resolve("language"));
            autoMigrationRebuildsAndPreservesValues(temp.resolve("auto"));
            version520MigratesTo530AndRenamesDeleteConfirmations(temp.resolve("from520"));
            version510MigratesTo530WithoutRelayReset(temp.resolve("from510"));
            version500MigratesDirectTo530AndResetsRelayV2(temp.resolve("from500to520"));
            oldVersionBacksUpAndResetsRelayV2(temp.resolve("old"));
            unversionedConfigMigratesAndBacksUp(temp.resolve("unversioned"));
            relayPeerPoliciesAreAugmentedCompatibly();
            System.out.println("CONFIG MIGRATION HARNESS PASS: 11 scenarios, " + checks + " assertions");
        } finally {
            deleteTree(temp);
        }
    }

    @SuppressWarnings("unchecked")
    private static void relayPeerPoliciesAreAugmentedCompatibly() throws Exception {
        LinkedHashMap<String,Object> legacyPeer = new LinkedHashMap<>();
        legacyPeer.put("url", "https://legacy.example/api/relay");
        legacyPeer.put("enabled", true);

        LinkedHashMap<String,Object> partialSend = new LinkedHashMap<>();
        partialSend.put("event", false);
        LinkedHashMap<String,Object> partialPeer = new LinkedHashMap<>();
        partialPeer.put("url", "https://partial.example/api/relay");
        partialPeer.put("send", partialSend);
        partialPeer.put("receive", false); // intentional scalar shorthand must be preserved

        LinkedHashMap<String,Object> group = new LinkedHashMap<>();
        group.put("id", "main");
        group.put("peers", java.util.List.of(legacyPeer, partialPeer));
        LinkedHashMap<String,Object> actual = new LinkedHashMap<>();
        actual.put("server-relay.groups", java.util.List.of(group));

        var method = PortableConfigMigration.class.getDeclaredMethod("augmentRelayPeerPolicies", Map.class);
        method.setAccessible(true);
        int inserted = ((Number) method.invoke(null, actual)).intValue();
        check(inserted == 11, "peer policy augmentation inserted unexpected number of defaults: " + inserted);

        java.util.List<Object> groups = (java.util.List<Object>) actual.get("server-relay.groups");
        Map<String,Object> rebuiltGroup = (Map<String,Object>) groups.get(0);
        java.util.List<Object> peers = (java.util.List<Object>) rebuiltGroup.get("peers");
        Map<String,Object> rebuiltLegacy = (Map<String,Object>) peers.get(0);
        Map<String,Object> legacySend = (Map<String,Object>) rebuiltLegacy.get("send");
        Map<String,Object> legacyReceive = (Map<String,Object>) rebuiltLegacy.get("receive");
        for (String key : java.util.List.of("public-chat", "event", "dm", "profile")) {
            check(Boolean.TRUE.equals(legacySend.get(key)), "legacy peer send." + key + " should default true");
            check(Boolean.TRUE.equals(legacyReceive.get(key)), "legacy peer receive." + key + " should default true");
        }

        Map<String,Object> rebuiltPartial = (Map<String,Object>) peers.get(1);
        Map<String,Object> rebuiltPartialSend = (Map<String,Object>) rebuiltPartial.get("send");
        check(Boolean.FALSE.equals(rebuiltPartialSend.get("event")), "explicit send.event=false was overwritten");
        for (String key : java.util.List.of("public-chat", "dm", "profile")) {
            check(Boolean.TRUE.equals(rebuiltPartialSend.get(key)), "missing partial send." + key + " should default true");
        }
        check(Boolean.FALSE.equals(rebuiltPartial.get("receive")), "scalar receive=false shorthand was not preserved");
        int secondPass = ((Number) method.invoke(null, actual)).intValue();
        check(secondPass == 0, "peer policy augmentation must be idempotent");
    }

    private static PortableConfigMigration.ResourceOpener opener() {
        return name -> {
            String text;
            if ("config.yml".equals(name)) text = CURRENT_EN;
            else if ("config-templates/config-ko-KR.yml".equals(name)) text = CURRENT_KO;
            else if (name.endsWith("config-5.2.0.yml")) text = BASE_520;
            else if (name.endsWith("config-5.1.0.yml")) text = BASE_510;
            else if (name.endsWith("config-5.0.0.yml")) text = BASE_500;
            else if (name.endsWith("config-4.5.5.yml")) text = BASE_455;
            else text = BASE_455;
            return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        };
    }

    private static PortableConfigMigration.ResourceOpener opener510() {
        return name -> {
            String text;
            if ("config.yml".equals(name)) text = BASE_510;
            else if ("config-templates/config-ko-KR.yml".equals(name)) text = BASE_510.replace("en-US", "ko-KR");
            else if (name.endsWith("config-5.0.0.yml")) text = BASE_500;
            else if (name.endsWith("config-4.5.5.yml")) text = BASE_455;
            else text = BASE_455;
            return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        };
    }

    private static Map<String,Object> snapshot(InputStream input) throws Exception {
        String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        Deque<Node> stack = new ArrayDeque<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) continue;
            int colon = trimmed.indexOf(':');
            if (colon <= 0) continue;
            int indent = leadingSpaces(line);
            while (!stack.isEmpty() && stack.peekLast().indent >= indent) stack.removeLast();
            String key = trimmed.substring(0, colon).trim();
            String raw = stripInlineComment(trimmed.substring(colon + 1).trim());
            String path = join(stack, key);
            if (raw.isEmpty()) {
                stack.addLast(new Node(indent, key));
            } else {
                out.put(path, scalar(raw));
            }
        }
        return out;
    }

    private static void fixedCurrentSkipsSameVersion(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = CURRENT_EN.replace("enabled: true", "enabled: false");
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("config-migration-5.3.0.yml"), "stale", StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.3.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        check(!r.changed(), "fixed current version should not rewrite same-language config");
        check(!r.automaticMigrationEnabled(), "fixed current version should keep automatic migration disabled");
        check(!Files.exists(dir.resolve("config-migration-5.3.0.yml")), "fixed current version should remove stale report");
        check(Boolean.FALSE.equals(snapshot(Files.newInputStream(path)).get("feature.enabled")), "fixed current value changed unexpectedly");
    }


    private static void fixedCurrentRemovesRetiredPreview(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = CURRENT_EN.replace("notify-reactions: true", "notify-reactions: true\n  show-message-preview: false")
                .replace("enabled: true", "enabled: false");
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.3.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed(), "fixed current config should be rebuilt when a retired preview option remains");
        check(!r.automaticMigrationEnabled(), "retired-setting cleanup must not enable automatic migration");
        check(!values.containsKey("notifications.show-message-preview"), "retired message-preview option was not removed");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "retired-setting cleanup failed to preserve operator values");
    }

    private static void fixedCurrentRenamesMessageDeleteConfirmations(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = CURRENT_EN
                .replace("confirm-delete: true", "confirm-hide: false");
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.3.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed(), "legacy message-hide confirmation keys should trigger a rebuild");
        check(Boolean.FALSE.equals(values.get("direct-message.confirm-delete")), "DM confirm-hide value was not migrated to confirm-delete");
        check(Boolean.FALSE.equals(values.get("group-chat.confirm-delete")), "group confirm-hide value was not migrated to confirm-delete");
        check(!values.containsKey("direct-message.confirm-hide"), "retired DM confirm-hide key survived migration");
        check(!values.containsKey("group-chat.confirm-hide"), "retired group confirm-hide key survived migration");
    }

    private static void languageChangeRebuildsWithoutAutoMigration(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = CURRENT_EN
                .replace("language: \"en-US\"", "language: \"ko-KR\"")
                .replace("enabled: true", "enabled: false");
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.3.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed(), "comment-language change should rebuild physical config");
        check(!r.automaticMigrationEnabled(), "language-only rebuild must not enable automatic migration");
        check(text.contains("# KWC config-comment-language: ko-KR"), "Korean template marker missing after language rebuild");
        check("5.3.0".equals(values.get("config-version")), "language rebuild changed fixed version marker");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "language rebuild failed to preserve operator value");
    }

    private static void autoMigrationRebuildsAndPreservesValues(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = """
                # KWC config-comment-language: en-US
                config-version: "5.3.0_auto_migration"
                ui:
                  language: "en-US"
                feature:
                  enabled: false
                chat:
                  conversation-archive:
                    enabled: false
                    max-archives-per-user: 55
                server-relay:
                  enabled: false
                  groups: []
                """;
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.3.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.automaticMigrationEnabled(), "auto marker should keep automatic migration enabled");
        check(r.insertedSettings() >= 2, "auto migration should detect representative missing settings");
        check("5.3.0_auto_migration".equals(values.get("config-version")), "auto marker was not preserved");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "auto migration failed to preserve operator value");
        check(Long.valueOf(42).equals(values.get("new-section.inserted-setting")), "new default was not inserted in current template location");
        check(Boolean.FALSE.equals(values.get("chat.conversation-archive.enabled")), "auto migration failed to preserve conversation archive OFF");
        check(Long.valueOf(55).equals(values.get("chat.conversation-archive.max-archives-per-user")), "auto migration failed to preserve configured archive-count quota");
        check(Long.valueOf(1000).equals(values.get("chat.conversation-archive.max-messages-per-archive")), "auto migration did not insert per-archive message quota default");
        check(Long.valueOf(10000).equals(values.get("chat.conversation-archive.max-messages-per-user")), "auto migration did not insert per-account archive message quota default");
        check(Boolean.FALSE.equals(values.get("chat.typing-indicator.open-chat.enabled")), "auto migration did not insert open-chat typing default OFF");
        check(Boolean.TRUE.equals(values.get("chat.typing-indicator.dm.enabled")), "auto migration did not insert DM typing default ON");
        check(Boolean.TRUE.equals(values.get("chat.typing-indicator.group-chat.enabled")), "auto migration did not insert group typing default ON");
        check(Boolean.TRUE.equals(values.get("notifications.notify-reactions")), "auto migration did not insert the Reactions notification default");
        check(Files.isRegularFile(dir.resolve("config-migration-5.3.0.yml")), "auto migration report missing");
    }

    private static void version520MigratesTo530AndRenamesDeleteConfirmations(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = BASE_520
                .replace("confirm-hide: true", "confirm-hide: false")
                .replace("feature:\n  enabled: true", "feature:\n  enabled: false");
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.3.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed() && r.automaticMigrationEnabled(), "5.2.0 should migrate into 5.3.0 auto mode");
        check("5.3.0_auto_migration".equals(values.get("config-version")), "5.2.0 -> 5.3.0 migration marker mismatch");
        check(Boolean.FALSE.equals(values.get("direct-message.confirm-delete")), "5.2.0 DM confirm-hide value was not preserved as confirm-delete");
        check(Boolean.FALSE.equals(values.get("group-chat.confirm-delete")), "5.2.0 group confirm-hide value was not preserved as confirm-delete");
        check(!values.containsKey("direct-message.confirm-hide"), "retired DM confirm-hide survived 5.3.0 migration");
        check(!values.containsKey("group-chat.confirm-hide"), "retired group confirm-hide survived 5.3.0 migration");
        check(Boolean.TRUE.equals(values.get("server-relay.enabled")), "5.2.0 -> 5.3.0 should preserve Relay v2 enabled state");
        check(hasBackup(dir, "5.3.0"), "5.2.0 fixed config backup missing before 5.3.0 migration");
    }

    private static void version510MigratesTo530WithoutRelayReset(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = """
                # KWC config-comment-language: en-US
                config-version: "5.1.0"
                ui:
                  language: "en-US"
                feature:
                  enabled: false
                  label: "operator-value"
                new-section:
                  inserted-setting: 42
                server-relay:
                  enabled: true
                  groups: []
                """;
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.3.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed() && r.automaticMigrationEnabled(), "5.1.0 should migrate into 5.3.0 auto mode");
        check("5.3.0_auto_migration".equals(values.get("config-version")), "5.1.0 -> 5.3.0 migration marker mismatch");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "5.1.0 -> 5.3.0 migration lost operator value");
        check(Boolean.TRUE.equals(values.get("chat.conversation-archive.enabled")), "5.1.0 -> 5.3.0 migration did not insert conversation archive default");
        check(Long.valueOf(100).equals(values.get("chat.conversation-archive.max-archives-per-user")), "5.1.0 -> 5.3.0 migration did not insert archive-count quota default");
        check(Long.valueOf(1000).equals(values.get("chat.conversation-archive.max-messages-per-archive")), "5.1.0 -> 5.3.0 migration did not insert per-archive quota default");
        check(Long.valueOf(10000).equals(values.get("chat.conversation-archive.max-messages-per-user")), "5.1.0 -> 5.3.0 migration did not insert per-account archive quota default");
        check(Boolean.FALSE.equals(values.get("chat.typing-indicator.open-chat.enabled")), "5.1.0 -> 5.3.0 migration did not insert open-chat typing default OFF");
        check(Boolean.TRUE.equals(values.get("chat.typing-indicator.dm.enabled")), "5.1.0 -> 5.3.0 migration did not insert DM typing default ON");
        check(Boolean.TRUE.equals(values.get("chat.typing-indicator.group-chat.enabled")), "5.1.0 -> 5.3.0 migration did not insert group typing default ON");
        check(Boolean.TRUE.equals(values.get("notifications.notify-reactions")), "5.1.0 -> 5.3.0 migration did not insert Reactions notification default");
        check(Boolean.TRUE.equals(values.get("server-relay.enabled")), "5.1.0 -> 5.3.0 should preserve relay enabled state");
        check(hasBackup(dir, "5.3.0"), "5.1.0 fixed config backup missing");
        check(Files.isRegularFile(dir.resolve("config-migration-5.3.0.yml")), "5.1.0 -> 5.3.0 migration report missing");
    }

    private static void version500MigratesDirectTo530AndResetsRelayV2(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = """
                # KWC config-comment-language: en-US
                config-version: "5.0.0"
                ui:
                  language: "en-US"
                feature:
                  enabled: false
                  label: "operator-value"
                server-relay:
                  enabled: true
                  shared-secret: "legacy-secret"
                """;
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.3.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed() && r.automaticMigrationEnabled(), "5.0.0 should migrate directly into 5.3.0 auto mode");
        check("5.3.0_auto_migration".equals(values.get("config-version")), "5.0.0 -> 5.3.0 migration marker mismatch");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "5.0.0 -> 5.3.0 migration lost operator value");
        check(Boolean.FALSE.equals(values.get("server-relay.enabled")), "5.0.0 -> 5.3.0 must still reset legacy relay disabled");
        check(!values.containsKey("server-relay.shared-secret"), "5.0.0 -> 5.3.0 must not carry legacy relay secret");
        check(hasBackup(dir, "5.3.0"), "5.0.0 -> 5.3.0 fixed config backup missing");
    }

    private static void oldVersionBacksUpAndResetsRelayV2(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = """
                # KWC config-comment-language: en-US
                config-version: "5.0.0"
                ui:
                  language: "en-US"
                feature:
                  enabled: false
                  label: "operator-value"
                server-relay:
                  enabled: true
                  shared-secret: "legacy-secret"
                """;
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.1.0", opener510(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed() && r.automaticMigrationEnabled(), "old version should migrate into auto mode");
        check("5.1.0_auto_migration".equals(values.get("config-version")), "old-version migration marker mismatch");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "old-version migration lost operator boolean");
        check("operator-value".equals(values.get("feature.label")), "old-version migration lost operator string");
        check(Boolean.FALSE.equals(values.get("server-relay.enabled")), "5.0.0 -> 5.1.0 relay must reset disabled");
        check(!values.containsKey("server-relay.shared-secret"), "legacy relay secret must not be carried into v2 config");
        check(hasBackup(dir, "5.1.0"), "old fixed config backup missing");
        check(Files.isRegularFile(dir.resolve("config-migration-5.1.0.yml")), "old-version migration report missing");
    }

    private static void unversionedConfigMigratesAndBacksUp(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = """
                # KWC config-comment-language: en-US
                ui:
                  language: "en-US"
                feature:
                  enabled: false
                """;
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.3.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed() && r.automaticMigrationEnabled(), "unversioned config should enter automatic migration");
        check("5.3.0_auto_migration".equals(values.get("config-version")), "unversioned config did not receive migration marker");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "unversioned migration lost operator value");
        check(hasBackup(dir, "5.3.0"), "unversioned config backup missing");
        check(Files.isRegularFile(dir.resolve("config-reference-5.3.0.yml")), "unversioned migration reference missing");
    }

    private static boolean hasBackup(Path dir, String targetVersion) throws Exception {
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.getFileName().toString().startsWith("config-backup-before-" + targetVersion + "-") && p.getFileName().toString().endsWith(".yml"));
        }
    }

    private static Object scalar(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) return value.substring(1, value.length() - 1);
        if (value.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (value.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (value.equals("[]")) return java.util.List.of();
        try { return Long.valueOf(value); } catch (NumberFormatException ignored) {}
        return value;
    }

    private static String stripInlineComment(String raw) {
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"' && (i == 0 || raw.charAt(i - 1) != '\\')) quoted = !quoted;
            if (c == '#' && !quoted && (i == 0 || Character.isWhitespace(raw.charAt(i - 1)))) return raw.substring(0, i).trim();
        }
        return raw;
    }

    private static int leadingSpaces(String line) { int n = 0; while (n < line.length() && line.charAt(n) == ' ') n++; return n; }
    private static String join(Deque<Node> stack, String key) {
        StringBuilder out = new StringBuilder();
        for (Node n : stack) { if (!out.isEmpty()) out.append('.'); out.append(n.key); }
        if (!out.isEmpty()) out.append('.');
        return out.append(key).toString();
    }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
    private record Node(int indent, String key) {}
}
