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

/** Representative file-I/O regression harness for the loader-neutral 5.1.0 config migration engine. */
public final class PortableConfigMigrationHarness {
    private static int checks;

    private static final String CURRENT_EN = """
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

    private static final String CURRENT_KO = """
            # KWC config-comment-language: ko-KR
            # 한국어 구성 주석 템플릿
            config-version: "5.1.0"
            ui:
              language: "ko-KR"
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
            languageChangeRebuildsWithoutAutoMigration(temp.resolve("language"));
            autoMigrationRebuildsAndPreservesValues(temp.resolve("auto"));
            oldVersionBacksUpAndResetsRelayV2(temp.resolve("old"));
            unversionedConfigMigratesAndBacksUp(temp.resolve("unversioned"));
            System.out.println("CONFIG MIGRATION HARNESS PASS: 5 scenarios, " + checks + " assertions");
        } finally {
            deleteTree(temp);
        }
    }

    private static PortableConfigMigration.ResourceOpener opener() {
        return name -> {
            String text;
            if ("config.yml".equals(name)) text = CURRENT_EN;
            else if ("config-templates/config-ko-KR.yml".equals(name)) text = CURRENT_KO;
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
        Files.writeString(dir.resolve("config-migration-5.1.0.yml"), "stale", StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.1.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        check(!r.changed(), "fixed current version should not rewrite same-language config");
        check(!r.automaticMigrationEnabled(), "fixed current version should keep automatic migration disabled");
        check(!Files.exists(dir.resolve("config-migration-5.1.0.yml")), "fixed current version should remove stale report");
        check(Boolean.FALSE.equals(snapshot(Files.newInputStream(path)).get("feature.enabled")), "fixed current value changed unexpectedly");
    }

    private static void languageChangeRebuildsWithoutAutoMigration(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = CURRENT_EN
                .replace("language: \"en-US\"", "language: \"ko-KR\"")
                .replace("enabled: true", "enabled: false");
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.1.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed(), "comment-language change should rebuild physical config");
        check(!r.automaticMigrationEnabled(), "language-only rebuild must not enable automatic migration");
        check(text.contains("# KWC config-comment-language: ko-KR"), "Korean template marker missing after language rebuild");
        check("5.1.0".equals(values.get("config-version")), "language rebuild changed fixed version marker");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "language rebuild failed to preserve operator value");
    }

    private static void autoMigrationRebuildsAndPreservesValues(Path dir) throws Exception {
        Files.createDirectories(dir);
        String config = """
                # KWC config-comment-language: en-US
                config-version: "5.1.0_auto_migration"
                ui:
                  language: "en-US"
                feature:
                  enabled: false
                server-relay:
                  enabled: false
                  groups: []
                """;
        Path path = dir.resolve("config.yml");
        Files.writeString(path, config, StandardCharsets.UTF_8);
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.1.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.automaticMigrationEnabled(), "auto marker should keep automatic migration enabled");
        check(r.insertedSettings() >= 2, "auto migration should detect representative missing settings");
        check("5.1.0_auto_migration".equals(values.get("config-version")), "auto marker was not preserved");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "auto migration failed to preserve operator value");
        check(Long.valueOf(42).equals(values.get("new-section.inserted-setting")), "new default was not inserted in current template location");
        check(Files.isRegularFile(dir.resolve("config-migration-5.1.0.yml")), "auto migration report missing");
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
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.1.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed() && r.automaticMigrationEnabled(), "old version should migrate into auto mode");
        check("5.1.0_auto_migration".equals(values.get("config-version")), "old-version migration marker mismatch");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "old-version migration lost operator boolean");
        check("operator-value".equals(values.get("feature.label")), "old-version migration lost operator string");
        check(Boolean.FALSE.equals(values.get("server-relay.enabled")), "5.0.0 -> 5.1.0 relay must reset disabled");
        check(!values.containsKey("server-relay.shared-secret"), "legacy relay secret must not be carried into v2 config");
        check(hasBackup(dir), "old fixed config backup missing");
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
        PortableConfigMigration.Result r = PortableConfigMigration.reconcile(dir, "5.1.0", opener(), PortableConfigMigrationHarness::snapshot, null);
        Map<String,Object> values = snapshot(Files.newInputStream(path));
        check(r.changed() && r.automaticMigrationEnabled(), "unversioned config should enter automatic migration");
        check("5.1.0_auto_migration".equals(values.get("config-version")), "unversioned config did not receive migration marker");
        check(Boolean.FALSE.equals(values.get("feature.enabled")), "unversioned migration lost operator value");
        check(hasBackup(dir), "unversioned config backup missing");
        check(Files.isRegularFile(dir.resolve("config-reference-5.1.0.yml")), "unversioned migration reference missing");
    }

    private static boolean hasBackup(Path dir) throws Exception {
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.getFileName().toString().startsWith("config-backup-before-5.1.0-") && p.getFileName().toString().endsWith(".yml"));
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
