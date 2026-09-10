package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * ContentFilterWordListStore는 KWC 상태를 메모리/JSONL/SQLite 같은 영속 매체에 저장하고 조회하는 계층이다.
 * ContentFilterWordListStore is a persistence layer storing and reading KWC state from memory, JSONL, SQLite, or another backing store.
 *
 * 조회 visibility와 mutation 권한을 분리하고, transaction/atomic rewrite가 필요한 작업은 중간 실패로 데이터가 반쯤 적용되지 않게 해야 한다.
 * Keep read visibility separate from mutation authorization, and use transactions/atomic rewrites where partial failure could leave inconsistent data.
 */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** UTF-8 bulk filter-word lists stored under filter-lists/. */
public final class ContentFilterWordListStore {
    private ContentFilterWordListStore() {}

    public static final String DIRECTORY = "filter-lists";
    private static final String DISABLED_SUFFIX = ".disabled";
    private static final String ACTIONS_FILE = ".list-actions.json";
    private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;

    public record ListFile(String name, boolean enabled, String action, int wordCount, long sizeBytes) {}

    public static Path directory(Path dataDirectory) throws IOException {
        Path dir = dataDirectory.resolve(DIRECTORY);
        Files.createDirectories(dir);
        return dir;
    }

    public static List<ListFile> list(Path dataDirectory) throws IOException {
        Path dir = directory(dataDirectory);
        TreeMap<String,ListFile> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String,String> actions = readActions(dir);
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).toList()) {
                String stored = path.getFileName().toString();
                if (!Files.isRegularFile(path) || !isStoredListName(stored)) continue;
                boolean enabled = !stored.endsWith(DISABLED_SUFFIX);
                String logical = enabled ? stored : stored.substring(0, stored.length() - DISABLED_SUFFIX.length());
                ListFile info = new ListFile(logical, enabled, actionFor(actions, logical), readWords(path).size(), Files.size(path));
                ListFile previous = byName.get(logical);
                if (previous == null || enabled) byName.put(logical, info); // active file wins if both exist
            }
        }
        return new ArrayList<>(byName.values());
    }

    public static List<ContentFilterRule> loadRules(Path dataDirectory) throws IOException {
        ArrayList<ContentFilterRule> rules = new ArrayList<>();
        for (ListFile info : list(dataDirectory)) {
            if (!info.enabled()) continue;
            Path path = resolveExisting(dataDirectory, info.name());
            List<String> words = readWords(path);
            if (words.isEmpty()) continue;
            ContentFilterRule rule = new ContentFilterRule();
            rule.id = listRuleId(info.name());
            rule.enabled = true;
            rule.action = normalizeAction(info.action());
            rule.words = new ArrayList<>(words);
            rule.normalize();
            rules.add(rule);
        }
        return rules;
    }


    public static ListFile info(Path dataDirectory, String requestedName) throws IOException {
        String name = normalizeLogicalName(requestedName);
        for (ListFile file : list(dataDirectory)) {
            if (file.name().equalsIgnoreCase(name)) return file;
        }
        throw new NoSuchFileException(name);
    }

    public static String readText(Path dataDirectory, String name) throws IOException {
        Path path = resolveExisting(dataDirectory, name);
        checkSize(path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public static ListFile save(Path dataDirectory, String requestedName, String text, Boolean enabledOverride, String actionOverride) throws IOException {
        String name = normalizeLogicalName(requestedName);
        Path dir = directory(dataDirectory);
        Path active = dir.resolve(name);
        Path disabled = dir.resolve(name + DISABLED_SUFFIX);
        boolean currentEnabled = Files.exists(active) || !Files.exists(disabled);
        boolean enabled = enabledOverride == null ? currentEnabled : enabledOverride;
        Map<String,String> actions = readActions(dir);
        String action = actionOverride == null ? actionFor(actions, name) : normalizeAction(actionOverride);
        String normalizedText = normalizeText(text);
        if (normalizedText.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw new IOException("list_too_large");
        Path target = enabled ? active : disabled;
        Path other = enabled ? disabled : active;
        Path temp = dir.resolve("." + name + ".tmp-" + UUID.randomUUID());
        Files.writeString(temp, normalizedText, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Files.deleteIfExists(temp);
        }
        Files.deleteIfExists(other);
        actions.put(name, action);
        writeActions(dir, actions);
        return new ListFile(name, enabled, action, readWords(target).size(), Files.size(target));
    }

    public static ListFile setEnabled(Path dataDirectory, String requestedName, boolean enabled) throws IOException {
        String name = normalizeLogicalName(requestedName);
        Path dir = directory(dataDirectory);
        Path active = dir.resolve(name);
        Path disabled = dir.resolve(name + DISABLED_SUFFIX);
        Path source = Files.exists(active) ? active : disabled;
        Map<String,String> actions = readActions(dir);
        String action = actionFor(actions, name);
        if (!Files.exists(source)) throw new NoSuchFileException(name);
        Path target = enabled ? active : disabled;
        Path other = enabled ? disabled : active;
        if (!source.equals(target)) {
            Files.deleteIfExists(target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(other);
        return new ListFile(name, enabled, action, readWords(target).size(), Files.size(target));
    }

    public static boolean delete(Path dataDirectory, String requestedName) throws IOException {
        String name = normalizeLogicalName(requestedName);
        Path dir = directory(dataDirectory);
        boolean removed = Files.deleteIfExists(dir.resolve(name));
        removed |= Files.deleteIfExists(dir.resolve(name + DISABLED_SUFFIX));
        if (removed) {
            Map<String,String> actions = readActions(dir);
            removeAction(actions, name);
            writeActions(dir, actions);
        }
        return removed;
    }

    public static int activeWordCount(Path dataDirectory) throws IOException {
        int total = 0;
        for (ListFile file : list(dataDirectory)) if (file.enabled()) total += file.wordCount();
        return total;
    }

    private static String normalizeAction(String action) {
        return "mask".equalsIgnoreCase(String.valueOf(action == null ? "" : action).trim()) ? "mask" : "block";
    }

    private static String actionFor(Map<String,String> actions, String name) {
        if (actions == null || actions.isEmpty()) return "block";
        for (Map.Entry<String,String> e : actions.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return normalizeAction(e.getValue());
        }
        return "block";
    }

    private static void removeAction(Map<String,String> actions, String name) {
        if (actions == null || actions.isEmpty()) return;
        String found = null;
        for (String key : actions.keySet()) {
            if (key.equalsIgnoreCase(name)) { found = key; break; }
        }
        if (found != null) actions.remove(found);
    }

    private static Map<String,String> readActions(Path dir) throws IOException {
        Path path = dir.resolve(ACTIONS_FILE);
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        if (!Files.isRegularFile(path)) return out;
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        for (Map.Entry<String,String> e : JsonUtil.parseFlatObject(raw).entrySet()) {
            String name = normalizeLogicalName(e.getKey());
            out.put(name, normalizeAction(e.getValue()));
        }
        return out;
    }

    private static void writeActions(Path dir, Map<String,String> actions) throws IOException {
        Path path = dir.resolve(ACTIONS_FILE);
        if (actions == null || actions.isEmpty()) {
            Files.deleteIfExists(path);
            return;
        }
        TreeMap<String,String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String,String> e : actions.entrySet()) sorted.put(normalizeLogicalName(e.getKey()), normalizeAction(e.getValue()));
        String json = JsonUtil.obj(sorted) + "\n";
        Path temp = dir.resolve(ACTIONS_FILE + ".tmp-" + UUID.randomUUID());
        Files.writeString(temp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Path resolveExisting(Path dataDirectory, String requestedName) throws IOException {
        String name = normalizeLogicalName(requestedName);
        Path dir = directory(dataDirectory);
        Path active = dir.resolve(name);
        if (Files.isRegularFile(active)) return active;
        Path disabled = dir.resolve(name + DISABLED_SUFFIX);
        if (Files.isRegularFile(disabled)) return disabled;
        throw new NoSuchFileException(name);
    }

    private static List<String> readWords(Path path) throws IOException {
        checkSize(path);
        LinkedHashSet<String> words = new LinkedHashSet<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String value = line.replace("\uFEFF", "").trim();
            if (value.isBlank() || value.startsWith("#")) continue;
            words.add(value);
        }
        return new ArrayList<>(words);
    }

    private static void checkSize(Path path) throws IOException {
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("list_too_large");
    }

    private static String normalizeText(String text) {
        String raw = String.valueOf(text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n');
        return raw.endsWith("\n") || raw.isEmpty() ? raw : raw + "\n";
    }

    public static String normalizeLogicalName(String requestedName) {
        String raw = String.valueOf(requestedName == null ? "" : requestedName).replace('\\', '/');
        int slash = raw.lastIndexOf('/');
        if (slash >= 0) raw = raw.substring(slash + 1);
        raw = raw.replaceAll("[\\x00-\\x1F\\x7F<>:\"/\\\\|?*]", "_").trim();
        while (raw.startsWith(".")) raw = raw.substring(1);
        if (raw.endsWith(DISABLED_SUFFIX)) raw = raw.substring(0, raw.length() - DISABLED_SUFFIX.length());
        if (!raw.toLowerCase(Locale.ROOT).endsWith(".txt")) raw += ".txt";
        if (raw.equalsIgnoreCase(".txt") || raw.isBlank()) raw = "words.txt";
        if (raw.length() > 120) raw = raw.substring(0, Math.min(116, raw.length())) + ".txt";
        return raw;
    }

    private static boolean isStoredListName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".txt" + DISABLED_SUFFIX);
    }

    private static String listRuleId(String name) {
        String stem = name;
        if (stem.toLowerCase(Locale.ROOT).endsWith(".txt")) stem = stem.substring(0, stem.length() - 4);
        String clean = ContentFilterRule.normalizedId(stem);
        if (clean.isBlank()) clean = "words";
        if (clean.length() > 44) clean = clean.substring(0, 44);
        String hash = SecurityUtil.sha256Hex(name).substring(0, 8);
        return "list." + clean + "." + hash;
    }
}
