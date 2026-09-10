package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * ReactionCatalogStore는 KWC 상태를 메모리/JSONL/SQLite 같은 영속 매체에 저장하고 조회하는 계층이다.
 * ReactionCatalogStore is a persistence layer storing and reading KWC state from memory, JSONL, SQLite, or another backing store.
 *
 * 조회 visibility와 mutation 권한을 분리하고, transaction/atomic rewrite가 필요한 작업은 중간 실패로 데이터가 반쯤 적용되지 않게 해야 한다.
 * Keep read visibility separate from mutation authorization, and use transactions/atomic rewrites where partial failure could leave inconsistent data.
 */
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Administrator-managed catalog used by the public-message reaction picker.
 *
 * <p>The catalog is deliberately separate from config.yml because it is an editable
 * UI asset rather than a server boot option. The same persisted file also stores
 * the administrator master reaction enable/disable state. Existing reactions are
 * never deleted when the feature is disabled or an icon is removed; these controls
 * govern whether new reactions may be activated.</p>
 */
public final class ReactionCatalogStore {
    public static final List<String> CATEGORY_IDS = List.of(
            "smileys", "people", "animals", "food", "activities", "objects", "symbols"
    );

    public static final class Snapshot {
        public final LinkedHashMap<String,List<String>> categories;
        public final boolean enabled;
        public final boolean customEmojiEnabled;
        public final boolean showActorList;
        public final LinkedHashMap<String,String> searchAliases;

        Snapshot(Map<String,List<String>> categories, boolean enabled, boolean customEmojiEnabled, boolean showActorList, Map<String,String> searchAliases) {
            this.categories = new LinkedHashMap<>();
            for (String id : CATEGORY_IDS) {
                List<String> values = categories == null ? null : categories.get(id);
                this.categories.put(id, values == null ? List.of() : List.copyOf(values));
            }
            this.enabled = enabled;
            this.customEmojiEnabled = customEmojiEnabled;
            this.showActorList = showActorList;
            this.searchAliases = new LinkedHashMap<>();
            if (searchAliases != null) this.searchAliases.putAll(searchAliases);
        }

        public String toJson() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("enabled", enabled);
            out.put("customEmojiEnabled", customEmojiEnabled);
            out.put("showActorList", showActorList);
            List<String> rows = new ArrayList<>();
            LinkedHashMap<String,String> searchNames = new LinkedHashMap<>();
            for (String id : CATEGORY_IDS) {
                List<String> items = categories.getOrDefault(id, List.of());
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("items", items);
                rows.add(JsonUtil.obj(row));
                for (String item : items) {
                    String name = unicodeSearchName(item);
                    if (!name.isBlank()) searchNames.putIfAbsent(item, name);
                }
            }
            return "{\"enabled\":" + enabled + ",\"customEmojiEnabled\":" + customEmojiEnabled
                    + ",\"showActorList\":" + showActorList
                    + ",\"categories\":[" + String.join(",", rows) + "]"
                    + ",\"searchNames\":" + JsonUtil.obj(searchNames)
                    + ",\"searchAliases\":" + JsonUtil.obj(searchAliases) + "}";
        }
    }

    private final Path file;
    private final Path aliasFile;
    private final CoreLogger logger;
    private final LinkedHashMap<String,List<String>> categories = new LinkedHashMap<>();
    private final LinkedHashMap<String,String> searchAliases = new LinkedHashMap<>();
    private boolean enabled = true;
    private boolean customEmojiEnabled = true;
    private boolean showActorList = true;

    public ReactionCatalogStore(Path dataDirectory, CoreLogger logger) {
        this.file = dataDirectory.resolve("reaction-catalog.json");
        this.aliasFile = dataDirectory.resolve("reaction-search-aliases.txt");
        this.logger = logger;
        resetMemoryToDefaults();
        load();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(categories, enabled, customEmojiEnabled, showActorList, searchAliases);
    }

    public synchronized boolean enabled() {
        return enabled;
    }

    public synchronized boolean showActorList() {
        return showActorList;
    }

    public synchronized boolean allows(String reaction) {
        if (!enabled) return false;
        String value = clean(reaction, 240);
        if (value.isBlank()) return false;
        if (value.startsWith(":") && value.endsWith(":") && value.length() > 2) return customEmojiEnabled;
        for (List<String> values : categories.values()) if (values.contains(value)) return true;
        return false;
    }

    public synchronized Snapshot save(Map<String,String> values) throws IOException {
        LinkedHashMap<String,List<String>> next = new LinkedHashMap<>();
        for (String id : CATEGORY_IDS) {
            String raw = values == null ? "" : String.valueOf(values.getOrDefault(id, ""));
            next.put(id, parseEmojiList(raw));
        }
        boolean nextEnabled = values == null || !values.containsKey("enabled")
                ? enabled
                : parseBoolean(values.get("enabled"), enabled);
        boolean custom = values == null || !values.containsKey("customEmojiEnabled")
                ? customEmojiEnabled
                : parseBoolean(values.get("customEmojiEnabled"), customEmojiEnabled);
        boolean showActors = values == null || !values.containsKey("showActorList")
                ? showActorList
                : parseBoolean(values.get("showActorList"), showActorList);
        LinkedHashMap<String,String> aliases = values == null || !values.containsKey("searchAliases")
                ? new LinkedHashMap<>(searchAliases)
                : parseAliasList(values.get("searchAliases"));

        categories.clear();
        categories.putAll(next);
        enabled = nextEnabled;
        customEmojiEnabled = custom;
        showActorList = showActors;
        searchAliases.clear();
        searchAliases.putAll(aliases);
        persist();
        return snapshot();
    }

    public synchronized Snapshot resetDefaults() throws IOException {
        resetMemoryToDefaults();
        persist();
        return snapshot();
    }

    private void resetMemoryToDefaults() {
        categories.clear();
        categories.put("smileys", list("😀 😃 😄 😁 😆 😅 😂 🙂 🙃 😉 😊 😍 🥰 😘 😎 🤔 😮 😢 😭 😡 🤯 🥳"));
        categories.put("people", list("👍 👎 👌 ✌️ 🤞 🤟 🤘 👏 🙌 🫶 🙏 💪 👀 🧠 ❤️ 💔 💯"));
        categories.put("animals", list("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐸 🐵 🐔 🐧 🐦 🦄 🐝 🦋 🌸 🌈"));
        categories.put("food", list("🍎 🍊 🍋 🍉 🍇 🍓 🍒 🍑 🍔 🍟 🍕 🌭 🍿 🍩 🍪 🎂 🍰 ☕ 🍺"));
        categories.put("activities", list("⚽ 🏀 🏈 ⚾ 🎾 🏐 🎮 🎲 🎯 🎵 🎤 🎧 🎬 📷 🚗 ✈️ 🚀 🎉 🎊"));
        categories.put("objects", list("💡 🔥 ⭐ ✨ ⚡ 💥 💎 🎁 🏆 🥇 📌 📎 🔒 🔑 🛠️ 🧪 💻 📱 ⏰"));
        categories.put("symbols", list("✅ ❌ ⭕ ❗ ❓ ⚠️ ♻️ ➕ ➖ ➡️ ⬆️ ⬇️ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪"));
        searchAliases.clear();
        searchAliases.putAll(loadDefaultAliases());
        enabled = true;
        customEmojiEnabled = true;
        showActorList = true;
    }

    private static List<String> list(String raw) {
        return parseEmojiList(raw);
    }

    private static List<String> parseEmojiList(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String normalized = String.valueOf(raw == null ? "" : raw)
                .replace('\r', ' ').replace('\n', ' ').replace(',', ' ');
        for (String part : normalized.split("\\s+")) {
            String value = clean(part, 64);
            if (value.isBlank() || !emojiLike(value)) continue;
            out.add(value);
            if (out.size() >= 128) break;
        }
        return new ArrayList<>(out);
    }

    private void load() {
        try {
            if (Files.isRegularFile(file)) {
                Map<String,String> parsed = JsonUtil.parseFlatObject(Files.readString(file, StandardCharsets.UTF_8));
                LinkedHashMap<String,List<String>> loaded = new LinkedHashMap<>();
                boolean any = false;
                for (String id : CATEGORY_IDS) {
                    if (parsed.containsKey(id)) {
                        loaded.put(id, parseEmojiList(parsed.get(id)));
                        any = true;
                    } else loaded.put(id, categories.getOrDefault(id, List.of()));
                }
                if (any) {
                    categories.clear();
                    categories.putAll(loaded);
                }
                if (parsed.containsKey("enabled")) enabled = parseBoolean(parsed.get("enabled"), true);
                if (parsed.containsKey("customEmojiEnabled")) customEmojiEnabled = parseBoolean(parsed.get("customEmojiEnabled"), true);
                if (parsed.containsKey("showActorList")) showActorList = parseBoolean(parsed.get("showActorList"), true);
            }
            if (Files.isRegularFile(aliasFile)) {
                searchAliases.clear();
                searchAliases.putAll(parseAliasList(Files.readString(aliasFile, StandardCharsets.UTF_8)));
            }
        } catch (Exception ex) {
            if (logger != null) logger.warn("Failed to load reaction catalog: " + ex.getMessage());
        }
    }

    private void persist() throws IOException {
        Files.createDirectories(file.getParent());
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("customEmojiEnabled", customEmojiEnabled);
        data.put("showActorList", showActorList);
        for (String id : CATEGORY_IDS) data.put(id, String.join(" ", categories.getOrDefault(id, List.of())));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, JsonUtil.obj(data) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        Path aliasTmp = aliasFile.resolveSibling(aliasFile.getFileName() + ".tmp");
        Files.writeString(aliasTmp, aliasListText(searchAliases), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(aliasTmp, aliasFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(aliasTmp, aliasFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private LinkedHashMap<String,String> loadDefaultAliases() {
        try (InputStream in = ReactionCatalogStore.class.getResourceAsStream("/reaction-search-aliases.txt")) {
            if (in == null) return new LinkedHashMap<>();
            return parseAliasList(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            if (logger != null) logger.warn("Failed to load default reaction search aliases: " + ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static LinkedHashMap<String,String> parseAliasList(String raw) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        String text = String.valueOf(raw == null ? "" : raw).replace("\r", "");
        for (String line : text.split("\n")) {
            String row = line.trim();
            if (row.isBlank() || row.startsWith("#")) continue;
            int split = row.indexOf('\t');
            if (split < 0) split = row.indexOf('=');
            if (split <= 0) continue;
            String emoji = clean(row.substring(0, split), 64);
            String words = clean(row.substring(split + 1), 512).replaceAll("\\s+", " ").trim();
            if (!emojiLike(emoji) || words.isBlank()) continue;
            out.put(emoji, words);
            if (out.size() >= 512) break;
        }
        return out;
    }

    private static String aliasListText(Map<String,String> aliases) {
        StringBuilder out = new StringBuilder();
        out.append("# KOKOTO WebChat Unicode reaction search aliases\n");
        out.append("# Format: emoji<TAB>keywords\n");
        if (aliases != null) for (Map.Entry<String,String> entry : aliases.entrySet()) {
            if (!emojiLike(entry.getKey())) continue;
            String words = clean(entry.getValue(), 512).replaceAll("\\s+", " ").trim();
            if (words.isBlank()) continue;
            out.append(entry.getKey()).append('\t').append(words).append('\n');
        }
        return out.toString();
    }

    private static String unicodeSearchName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length();) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xFE0E || cp == 0xFE0F || cp == 0x200D) continue;
            String name = Character.getName(cp);
            if (name == null || name.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(name.toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static boolean emojiLike(String raw) {
        if (raw == null || raw.isBlank() || raw.codePointCount(0, raw.length()) > 16) return false;
        boolean base = false;
        for (int i = 0; i < raw.length();) {
            int cp = raw.codePointAt(i); i += Character.charCount(cp);
            if (Character.isISOControl(cp) || Character.isWhitespace(cp)) return false;
            if ((cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF)
                    || (cp >= 0x2300 && cp <= 0x23FF) || (cp >= 0x2190 && cp <= 0x21FF)
                    || (cp >= 0x1F1E6 && cp <= 0x1F1FF) || cp == 0x00A9 || cp == 0x00AE
                    || cp == 0x2122 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299) base = true;
        }
        return base;
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        String value = String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
        if (Set.of("true", "yes", "on", "1").contains(value)) return true;
        if (Set.of("false", "no", "off", "0").contains(value)) return false;
        return fallback;
    }

    private static String clean(String raw, int max) {
        String value = String.valueOf(raw == null ? "" : raw).replaceAll("[\\r\\n\\u0000-\\u001f]", "").trim();
        return value.length() > max ? value.substring(0, max) : value;
    }
}
