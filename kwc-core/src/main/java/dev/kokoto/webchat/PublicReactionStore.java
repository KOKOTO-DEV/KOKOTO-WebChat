package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * PublicReactionStore는 KWC 상태를 메모리/JSONL/SQLite 같은 영속 매체에 저장하고 조회하는 계층이다.
 * PublicReactionStore is a persistence layer storing and reading KWC state from memory, JSONL, SQLite, or another backing store.
 *
 * 조회 visibility와 mutation 권한을 분리하고, transaction/atomic rewrite가 필요한 작업은 중간 실패로 데이터가 반쯤 적용되지 않게 해야 한다.
 * Keep read visibility separate from mutation authorization, and use transactions/atomic rewrites where partial failure could leave inconsistent data.
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/** Persistent message reaction state. The historical public-reactions.jsonl filename is retained for compatibility; actor UUIDs remain server/relay internal. */
public final class PublicReactionStore {
    public static final class Actor {
        public final String uuid;
        public final String label;
        Actor(String uuid, String label) { this.uuid = uuid; this.label = label; }
    }

    public static final class Summary {
        public final String value;
        public final int count;
        public final boolean mine;
        public final List<Actor> actors;
        Summary(String value, int count, boolean mine, List<Actor> actors) {
            this.value = value;
            this.count = count;
            this.mine = mine;
            this.actors = actors == null ? List.of() : List.copyOf(actors);
        }
    }

    private final Path file;
    private final CoreLogger logger;
    // message key -> reaction -> actor UUID -> last known safe display label
    private final Map<String, LinkedHashMap<String, LinkedHashMap<String,String>>> state = new LinkedHashMap<>();
    private int appendedSinceCompact;

    public PublicReactionStore(Path dataDirectory, CoreLogger logger) {
        this.file = dataDirectory.resolve("public-reactions.jsonl");
        this.logger = logger;
        load();
    }

    public synchronized boolean has(String messageKey, String actorUuid, String reaction) {
        LinkedHashMap<String, LinkedHashMap<String,String>> byReaction = state.get(clean(messageKey));
        LinkedHashMap<String,String> actors = byReaction == null ? null : byReaction.get(clean(reaction));
        return actors != null && actors.containsKey(cleanActor(actorUuid));
    }

    public synchronized boolean apply(String messageKey, String actorUuid, String reaction, boolean active) {
        return apply(messageKey, actorUuid, "", reaction, active);
    }

    public synchronized boolean apply(String messageKey, String actorUuid, String actorLabel, String reaction, boolean active) {
        String key = clean(messageKey);
        String actor = cleanActor(actorUuid);
        String label = cleanLabel(actorLabel);
        String value = clean(reaction);
        if (key.isBlank() || actor.isBlank() || value.isBlank()) return false;
        boolean changed = applyMemory(key, actor, label, value, active);
        // An unchanged active reaction may still carry a newer display label. Persist
        // that identity refresh so the tooltip does not retain a stale nickname.
        if (!changed && active) {
            LinkedHashMap<String, LinkedHashMap<String,String>> byReaction = state.get(key);
            LinkedHashMap<String,String> actors = byReaction == null ? null : byReaction.get(value);
            String previous = actors == null ? null : actors.get(actor);
            if (actors != null && !label.isBlank() && !Objects.equals(previous, label)) {
                actors.put(actor, label);
                changed = true;
            }
        }
        if (!changed) return false;
        append(key, actor, label, value, active);
        if (++appendedSinceCompact >= 1000) compact();
        return true;
    }

    public synchronized List<Summary> summary(String messageKey, String viewerUuid) {
        LinkedHashMap<String, LinkedHashMap<String,String>> byReaction = state.get(clean(messageKey));
        if (byReaction == null || byReaction.isEmpty()) return List.of();
        String viewer = cleanActor(viewerUuid);
        ArrayList<Summary> out = new ArrayList<>();
        for (Map.Entry<String, LinkedHashMap<String,String>> e : byReaction.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            ArrayList<Actor> actors = new ArrayList<>();
            for (Map.Entry<String,String> actor : e.getValue().entrySet()) actors.add(new Actor(actor.getKey(), actor.getValue()));
            out.add(new Summary(e.getKey(), e.getValue().size(), !viewer.isBlank() && e.getValue().containsKey(viewer), actors));
        }
        return out;
    }

    public synchronized void removeMessage(String messageKey) {
        if (state.remove(clean(messageKey)) != null) compact();
    }

    public synchronized void clear() {
        state.clear();
        compact();
    }

    private boolean applyMemory(String key, String actor, String label, String value, boolean active) {
        LinkedHashMap<String, LinkedHashMap<String,String>> byReaction = state.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        LinkedHashMap<String,String> actors = byReaction.computeIfAbsent(value, ignored -> new LinkedHashMap<>());
        boolean changed;
        if (active) {
            String old = actors.putIfAbsent(actor, label);
            changed = old == null;
            if (!changed && !label.isBlank() && !Objects.equals(old, label)) {
                actors.put(actor, label);
                changed = true;
            }
        } else {
            changed = actors.remove(actor) != null;
        }
        if (actors.isEmpty()) byReaction.remove(value);
        if (byReaction.isEmpty()) state.remove(key);
        return changed;
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                Map<String,String> m = JsonUtil.parseFlatObject(line);
                String key = clean(m.get("messageKey"));
                String actor = cleanActor(m.get("actorUuid"));
                String label = cleanLabel(m.get("actorLabel"));
                String reaction = clean(m.get("reaction"));
                boolean active = Boolean.parseBoolean(String.valueOf(m.getOrDefault("active", "false")));
                if (!key.isBlank() && !actor.isBlank() && !reaction.isBlank()) applyMemory(key, actor, label, reaction, active);
            }
        } catch (Exception ex) {
            if (logger != null) logger.warn("Failed to load public reactions: " + ex.getMessage());
        }
    }

    private void append(String key, String actor, String actorLabel, String reaction, boolean active) {
        try {
            Files.createDirectories(file.getParent());
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("messageKey", key);
            m.put("actorUuid", actor);
            if (!actorLabel.isBlank()) m.put("actorLabel", actorLabel);
            m.put("reaction", reaction);
            m.put("active", active);
            Files.writeString(file, JsonUtil.obj(m) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            if (logger != null) logger.warn("Failed to persist public reaction: " + ex.getMessage());
        }
    }

    public synchronized void compact() {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, LinkedHashMap<String, LinkedHashMap<String,String>>> message : state.entrySet()) {
                for (Map.Entry<String, LinkedHashMap<String,String>> reaction : message.getValue().entrySet()) {
                    for (Map.Entry<String,String> actor : reaction.getValue().entrySet()) {
                        Map<String,Object> m = new LinkedHashMap<>();
                        m.put("messageKey", message.getKey());
                        m.put("actorUuid", actor.getKey());
                        if (!actor.getValue().isBlank()) m.put("actorLabel", actor.getValue());
                        m.put("reaction", reaction.getKey());
                        m.put("active", true);
                        out.append(JsonUtil.obj(m)).append(System.lineSeparator());
                    }
                }
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, out.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            appendedSinceCompact = 0;
        } catch (IOException ex) {
            if (logger != null) logger.warn("Failed to compact public reactions: " + ex.getMessage());
        }
    }

    private static String clean(String value) {
        String s = String.valueOf(value == null ? "" : value).replaceAll("[\\r\\n\\u0000-\\u001f]", "").trim();
        return s.length() > 240 ? s.substring(0, 240) : s;
    }

    private static String cleanActor(String value) {
        String s = clean(value).toLowerCase(Locale.ROOT);
        return s.length() > 96 ? s.substring(0, 96) : s;
    }

    private static String cleanLabel(String value) {
        String s = LegacyText.stripColor(LegacyText.translateAlternateColorCodes('&', clean(value)));
        s = String.valueOf(s == null ? "" : s).replaceAll("\\s+", " ").trim();
        return s.length() > 96 ? s.substring(0, 96) : s;
    }
}
