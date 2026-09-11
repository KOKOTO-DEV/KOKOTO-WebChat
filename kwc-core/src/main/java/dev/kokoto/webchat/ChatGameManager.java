package dev.kokoto.webchat;

/* KWC 파일 안내 / KWC file guide
 * ChatGameManager는 웹과 게임에서 함께 사용하는 선착순/추첨 이벤트 목록과 참가자·당첨자를 저장한다.
 * ChatGameManager persists the list of first-come/lottery events, participants, and winners shared by web and game clients.
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Multiple server-local lightweight first-come/lottery events shared by web and game clients. */
public final class ChatGameManager {
    public record Result(boolean ok, String error, Map<String,Object> game, boolean changed) {}

    private final Path directory;
    private final java.util.Random random = new java.security.SecureRandom();
    private final LinkedHashMap<String,Game> games = new LinkedHashMap<>();

    private static final class Game {
        String id = "";
        String type = "lottery";
        String title = "";
        int maxParticipants;
        int winnerCount;
        boolean relayAnnouncements = true;
        String status = "open";
        String createdBy = "";
        long createdAt;
        final LinkedHashMap<String,String> participants = new LinkedHashMap<>();
        // Keep the immutable Minecraft/account username separately from the presentation label.
        final LinkedHashMap<String,String> participantUsernames = new LinkedHashMap<>();
        final List<String> winners = new ArrayList<>();
    }

    public ChatGameManager(Path dataDirectory) {
        this.directory = dataDirectory.resolve("chat-games");
        load();
    }

    /** Backward-compatible snapshot: newest active event, otherwise newest event. */
    public synchronized Result snapshot(String viewerUuid) {
        Game game = defaultGame();
        return new Result(true, "", snapshotMap(game, viewerUuid), false);
    }

    public synchronized Result snapshot(String gameId, String viewerUuid) {
        Game game = games.get(clean(gameId));
        return game == null ? fail("game_not_found", viewerUuid) : new Result(true, "", snapshotMap(game, viewerUuid), false);
    }

    public synchronized List<Map<String,Object>> list(String viewerUuid) {
        List<Game> ordered = new ArrayList<>(games.values());
        ordered.sort(Comparator.comparingLong((Game g) -> g.createdAt).reversed());
        List<Map<String,Object>> out = new ArrayList<>();
        for (Game game : ordered) out.add(snapshotMap(game, viewerUuid));
        return out;
    }

    public synchronized Result create(String type, String title, int maxParticipants, int winnerCount, String createdBy) {
        return create(type, title, maxParticipants, winnerCount, createdBy, true);
    }

    public synchronized Result create(String type, String title, int maxParticipants, int winnerCount, String createdBy, boolean relayAnnouncements) {
        type = clean(type).toLowerCase(Locale.ROOT);
        title = plain(title);
        if (!type.equals("firstcome") && !type.equals("lottery")) return fail("invalid_type", "");
        if (title.isBlank() || title.codePointCount(0, title.length()) > 80) return fail("invalid_title", "");
        if (winnerCount < 1 || winnerCount > 500) return fail("invalid_winner_count", "");
        // First-come has only one meaningful size: the number of winners. Keeping a
        // separate participant capacity allowed registration to continue after every
        // winner slot had already been filled. Normalize it at the authoritative store.
        if (type.equals("firstcome")) maxParticipants = winnerCount;
        // Lottery participant capacity starts at two but intentionally has no
        // product-level fixed upper cap. Java's positive int range remains the
        // practical parser/storage boundary. First-come does not use this field.
        else if (maxParticipants < 2) return fail("invalid_participant_count", "");
        else if (winnerCount > maxParticipants) return fail("invalid_winner_count", "");
        Game next = new Game();
        next.id = UUID.randomUUID().toString();
        next.type = type;
        next.title = title;
        next.maxParticipants = maxParticipants;
        next.winnerCount = winnerCount;
        next.relayAnnouncements = relayAnnouncements;
        next.createdBy = plain(createdBy);
        next.createdAt = System.currentTimeMillis();
        games.put(next.id, next);
        save(next);
        return changed(next, "");
    }

    public synchronized Result join(String uuid, String username, String label) {
        Game game = defaultJoinableGame();
        return game == null ? fail("game_not_found", uuid) : join(game.id, uuid, username, label);
    }

    public synchronized Result join(String gameId, String uuid, String username, String label) {
        Game game = games.get(clean(gameId));
        uuid = clean(uuid).toLowerCase(Locale.ROOT);
        username = plain(username);
        label = plain(label);
        if (game == null) return fail("game_not_found", uuid);
        if (!game.status.equals("open")) return fail(game, "game_not_open", uuid);
        if (uuid.isBlank() || username.isBlank()) return fail(game, "invalid_user", uuid);
        if (game.participants.containsKey(uuid)) return new Result(true, "already_joined", snapshotMap(game, uuid), false);
        if (game.participants.size() >= game.maxParticipants) return fail(game, "game_full", uuid);
        game.participants.put(uuid, label.isBlank() ? username : label);
        game.participantUsernames.put(uuid, username);
        if (game.type.equals("firstcome") && game.winners.size() < game.winnerCount) game.winners.add(uuid);
        if (game.type.equals("firstcome") && game.winners.size() >= game.winnerCount) game.status = "completed";
        else if (game.type.equals("lottery") && game.participants.size() >= game.maxParticipants) game.status = "ready";
        save(game);
        return changed(game, uuid);
    }

    public synchronized Result draw() {
        Game game = defaultManageableGame();
        return game == null ? fail("game_not_found", "") : draw(game.id);
    }

    public synchronized Result draw(String gameId) {
        Game game = games.get(clean(gameId));
        if (game == null) return fail("game_not_found", "");
        if (!game.type.equals("lottery")) return fail(game, "not_lottery", "");
        if (!game.status.equals("open") && !game.status.equals("ready")) return fail(game, "game_not_open", "");
        if (game.participants.size() < game.winnerCount) return fail(game, "not_enough_participants", "");
        List<String> candidates = new ArrayList<>(game.participants.keySet());
        Collections.shuffle(candidates, random);
        game.winners.clear();
        game.winners.addAll(candidates.subList(0, game.winnerCount));
        game.status = "completed";
        save(game);
        return changed(game, "");
    }

    /** Ends registration immediately and fixes the result with the participants collected so far. */
    public synchronized Result finish() {
        Game game = defaultManageableGame();
        return game == null ? fail("game_not_found", "") : finish(game.id);
    }

    public synchronized Result finish(String gameId) {
        Game game = games.get(clean(gameId));
        if (game == null) return fail("game_not_found", "");
        if (!game.status.equals("open") && !game.status.equals("ready")) return fail(game, "game_not_open", "");
        if (game.type.equals("lottery")) {
            List<String> candidates = new ArrayList<>(game.participants.keySet());
            Collections.shuffle(candidates, random);
            game.winners.clear();
            game.winners.addAll(candidates.subList(0, Math.min(game.winnerCount, candidates.size())));
        }
        game.status = "completed";
        save(game);
        return changed(game, "");
    }

    public synchronized Result close() {
        Game game = defaultGame();
        return game == null ? fail("game_not_found", "") : close(game.id);
    }

    public synchronized Result close(String gameId) {
        Game game = games.get(clean(gameId));
        if (game == null) return fail("game_not_found", "");
        game.status = "closed";
        save(game);
        return changed(game, "");
    }

    /** Permanently removes one event record from the event history/list. */
    public synchronized Result delete(String gameId) {
        String id = clean(gameId);
        Game game = games.remove(id);
        if (game == null) return fail("game_not_found", "");
        Map<String,Object> snapshot = snapshotMap(game, "");
        try { Files.deleteIfExists(directory.resolve(game.id + ".properties")); } catch (Exception ignored) {}
        return new Result(true, "", snapshot, true);
    }

    private Game defaultGame() {
        Game active = defaultManageableGame();
        if (active != null) return active;
        return games.values().stream().max(Comparator.comparingLong(g -> g.createdAt)).orElse(null);
    }

    private Game defaultJoinableGame() {
        return games.values().stream().filter(g -> "open".equals(g.status))
                .max(Comparator.comparingLong(g -> g.createdAt)).orElse(null);
    }

    private Game defaultManageableGame() {
        return games.values().stream().filter(g -> "open".equals(g.status) || "ready".equals(g.status))
                .max(Comparator.comparingLong(g -> g.createdAt)).orElse(null);
    }

    private Result changed(Game game, String viewerUuid) { return new Result(true, "", snapshotMap(game, viewerUuid), true); }
    private Result fail(String error, String viewerUuid) { return new Result(false, error, snapshotMap(defaultGame(), viewerUuid), false); }
    private Result fail(Game game, String error, String viewerUuid) { return new Result(false, error, snapshotMap(game, viewerUuid), false); }

    private Map<String,Object> snapshotMap(Game game, String viewerUuid) {
        if (game == null) return null;
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        out.put("id", game.id);
        out.put("type", game.type);
        out.put("title", game.title);
        out.put("maxParticipants", game.maxParticipants);
        out.put("winnerCount", game.winnerCount);
        out.put("relayAnnouncements", game.relayAnnouncements);
        out.put("status", game.status);
        out.put("createdBy", game.createdBy);
        out.put("createdAt", game.createdAt);
        out.put("joined", game.participants.containsKey(clean(viewerUuid).toLowerCase(Locale.ROOT)));
        List<Map<String,Object>> participants = new ArrayList<>();
        for (Map.Entry<String,String> entry : game.participants.entrySet()) {
            LinkedHashMap<String,Object> participant = new LinkedHashMap<>();
            String uuid = entry.getKey();
            String displayName = entry.getValue();
            participant.put("uuid", uuid);
            participant.put("label", displayName);
            participant.put("displayName", displayName);
            participant.put("username", game.participantUsernames.getOrDefault(uuid, ""));
            participants.add(participant);
        }
        List<Map<String,Object>> winners = new ArrayList<>();
        for (String uuid : game.winners) {
            LinkedHashMap<String,Object> winner = new LinkedHashMap<>();
            String displayName = game.participants.getOrDefault(uuid, uuid);
            winner.put("uuid", uuid);
            winner.put("label", displayName);
            winner.put("displayName", displayName);
            winner.put("username", game.participantUsernames.getOrDefault(uuid, ""));
            winners.add(winner);
        }
        out.put("participants", participants);
        out.put("winners", winners);
        return out;
    }

    private void save(Game game) {
        if (game == null || clean(game.id).isBlank()) return;
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(game.id + ".properties");
            Properties p = properties(game);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) { p.store(out, "KWC chat game"); }
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception ignored) {}
    }

    private static Properties properties(Game game) {
        Properties p = new Properties();
        p.setProperty("id", game.id); p.setProperty("type", game.type); p.setProperty("title", enc(game.title));
        p.setProperty("maxParticipants", String.valueOf(game.maxParticipants)); p.setProperty("winnerCount", String.valueOf(game.winnerCount));
        p.setProperty("relayAnnouncements", String.valueOf(game.relayAnnouncements));
        p.setProperty("status", game.status); p.setProperty("createdBy", enc(game.createdBy)); p.setProperty("createdAt", String.valueOf(game.createdAt));
        p.setProperty("participants", encodeParticipants(game.participants));
        p.setProperty("participantUsernames", encodeParticipants(game.participantUsernames));
        p.setProperty("winners", String.join(",", game.winners));
        return p;
    }

    private void load() {
        try {
            Files.createDirectories(directory);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.properties")) {
                for (Path file : stream) {
                    Game loaded = loadFile(file);
                    if (loaded != null) games.put(loaded.id, loaded);
                }
            }
        } catch (Exception ignored) {}
    }

    private Game loadFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties(); p.load(in);
            Game loaded = new Game();
            loaded.id = clean(p.getProperty("id", "")); loaded.type = p.getProperty("type", "lottery"); loaded.title = plain(dec(p.getProperty("title", "")));
            loaded.maxParticipants = integer(p.getProperty("maxParticipants"), 0); loaded.winnerCount = integer(p.getProperty("winnerCount"), 0);
            if ("firstcome".equalsIgnoreCase(loaded.type) && loaded.winnerCount > 0) loaded.maxParticipants = loaded.winnerCount;
            loaded.relayAnnouncements = Boolean.parseBoolean(p.getProperty("relayAnnouncements", "true"));
            loaded.status = p.getProperty("status", "closed"); loaded.createdBy = plain(dec(p.getProperty("createdBy", ""))); loaded.createdAt = longValue(p.getProperty("createdAt"));
            decodeParticipants(p.getProperty("participants", ""), loaded.participants);
            decodeParticipants(p.getProperty("participantUsernames", ""), loaded.participantUsernames);
            if (!loaded.participantUsernames.keySet().equals(loaded.participants.keySet())) return null;
            for (String winner : p.getProperty("winners", "").split(",")) if (!clean(winner).isBlank()) loaded.winners.add(clean(winner));
            if ("firstcome".equalsIgnoreCase(loaded.type) && ("open".equals(loaded.status) || "ready".equals(loaded.status))
                    && loaded.winners.size() >= loaded.winnerCount) loaded.status = "completed";
            if (!loaded.id.isBlank() && !loaded.title.isBlank() && loaded.maxParticipants > 0 && loaded.winnerCount > 0) return loaded;
        } catch (Exception ignored) {}
        return null;
    }

    private static String encodeParticipants(Map<String,String> values) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String,String> entry : values.entrySet()) out.add(enc(entry.getKey()) + ":" + enc(entry.getValue()));
        return String.join(";", out);
    }
    private static void decodeParticipants(String raw, Map<String,String> out) {
        for (String item : clean(raw).split(";")) { int split = item.indexOf(':'); if (split > 0) out.put(dec(item.substring(0, split)), plain(dec(item.substring(split + 1)))); }
    }
    private static String enc(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(clean(value).getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String value) { try { return new String(Base64.getUrlDecoder().decode(clean(value)), StandardCharsets.UTF_8); } catch (Exception ignored) { return ""; } }
    private static int integer(String value, int fallback) { try { return Integer.parseInt(clean(value)); } catch (Exception ignored) { return fallback; } }
    private static long longValue(String value) { try { return Long.parseLong(clean(value)); } catch (Exception ignored) { return 0L; } }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String plain(String value) {
        String stripped = LegacyText.stripColor(clean(value));
        if (stripped == null) return "";
        return stripped.replaceAll("(?i)&[0-9A-FK-ORX]", "").replace("**", "").trim();
    }
}
