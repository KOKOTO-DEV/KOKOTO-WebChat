package dev.kokoto.webchat;

/* KWC 파일 안내 / KWC file guide
 * ChatGameManager는 웹과 게임에서 함께 사용하는 이벤트 목록과 응답 상태를 저장한다.
 * ChatGameManager persists the shared web/game event list and response state.
 * 5.3.1 extends the existing first-come/lottery model with polls and role-based recruitment.
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Multiple server-local lightweight events shared by web and game clients. */
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
        // Automatic end policy. Multiple enabled conditions use first-match semantics.
        // First-come always auto-completes at its winner/capacity count; the flag is persisted for a uniform snapshot.
        boolean autoEndOnCapacity;
        int autoEndResponseCount;
        long autoEndAt;
        String completionReason = "";
        long completedAt;
        final LinkedHashMap<String,String> participants = new LinkedHashMap<>();
        // Keep the immutable Minecraft/account username separately from the presentation label.
        final LinkedHashMap<String,String> participantUsernames = new LinkedHashMap<>();
        final List<String> winners = new ArrayList<>();

        // Poll: ordered choices and one 1-based selection per voter.
        final List<String> pollOptions = new ArrayList<>();
        final LinkedHashMap<String,Integer> pollVotes = new LinkedHashMap<>();

        // Recruitment: ordered role capacities plus per-applicant role/state.
        // participants retains application order, which is also the wait-queue order.
        final LinkedHashMap<String,Integer> recruitmentRoles = new LinkedHashMap<>();
        final LinkedHashMap<String,String> participantRoles = new LinkedHashMap<>();
        final LinkedHashMap<String,String> participantStates = new LinkedHashMap<>();
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
        return create(type, title, maxParticipants, winnerCount, createdBy, relayAnnouncements, false, 0L);
    }

    public synchronized Result create(String type, String title, int maxParticipants, int winnerCount, String createdBy, boolean relayAnnouncements, boolean autoEndOnCapacity, long autoEndAt) {
        type = clean(type).toLowerCase(Locale.ROOT);
        title = plain(title);
        if (!type.equals("firstcome") && !type.equals("lottery")) return fail("invalid_type", "");
        if (!validTitle(title)) return fail("invalid_title", "");
        if (winnerCount < 1 || winnerCount > 500) return fail("invalid_winner_count", "");
        if (!validAutoEndAt(autoEndAt)) return fail("invalid_auto_end_time", "");
        // First-come has only one meaningful size: the winner count and always closes when it fills.
        if (type.equals("firstcome")) maxParticipants = winnerCount;
        else if (maxParticipants < 2) return fail("invalid_participant_count", "");
        else if (winnerCount > maxParticipants) return fail("invalid_winner_count", "");
        Game next = newGame(type, title, createdBy, relayAnnouncements);
        next.maxParticipants = maxParticipants;
        next.winnerCount = winnerCount;
        next.autoEndOnCapacity = type.equals("firstcome") || autoEndOnCapacity;
        next.autoEndAt = autoEndAt;
        return addNewGame(next);
    }

    public synchronized Result createPoll(String title, String rawOptions, String createdBy, boolean relayAnnouncements) {
        return createPoll(title, rawOptions, createdBy, relayAnnouncements, 0, 0L);
    }

    public synchronized Result createPoll(String title, String rawOptions, String createdBy, boolean relayAnnouncements, int autoEndResponseCount, long autoEndAt) {
        title = plain(title);
        if (!validTitle(title)) return fail("invalid_title", "");
        if (autoEndResponseCount < 0 || autoEndResponseCount > 500) return fail("invalid_auto_end_count", "");
        if (!validAutoEndAt(autoEndAt)) return fail("invalid_auto_end_time", "");
        List<String> options = parsePollOptions(rawOptions);
        if (options.size() < 2) return fail("invalid_poll_options", "");
        Game next = newGame("poll", title, createdBy, relayAnnouncements);
        next.pollOptions.addAll(options);
        // Polls intentionally have no participant capacity or winner count.
        next.maxParticipants = 0;
        next.winnerCount = 0;
        next.autoEndResponseCount = autoEndResponseCount;
        next.autoEndAt = autoEndAt;
        return addNewGame(next);
    }

    public synchronized Result createRecruitment(String title, String rawRoles, String createdBy, boolean relayAnnouncements) {
        return createRecruitment(title, rawRoles, createdBy, relayAnnouncements, false, 0L);
    }

    public synchronized Result createRecruitment(String title, String rawRoles, String createdBy, boolean relayAnnouncements, boolean autoEndOnCapacity, long autoEndAt) {
        title = plain(title);
        if (!validTitle(title)) return fail("invalid_title", "");
        if (!validAutoEndAt(autoEndAt)) return fail("invalid_auto_end_time", "");
        LinkedHashMap<String,Integer> roles = parseRecruitmentRoles(rawRoles);
        if (roles.isEmpty()) return fail("invalid_recruitment_roles", "");
        int capacity = 0;
        for (Integer value : roles.values()) capacity += Math.max(0, value == null ? 0 : value);
        if (capacity < 1 || capacity > 500) return fail("invalid_recruitment_roles", "");
        Game next = newGame("recruitment", title, createdBy, relayAnnouncements);
        next.recruitmentRoles.putAll(roles);
        next.maxParticipants = capacity; // accepted-slot capacity; wait queue may exceed it.
        next.winnerCount = 0;
        next.autoEndOnCapacity = autoEndOnCapacity;
        next.autoEndAt = autoEndAt;
        return addNewGame(next);
    }

    public synchronized Result join(String uuid, String username, String label) {
        Game game = defaultJoinableGame();
        return game == null ? fail("game_not_found", uuid) : join(game.id, uuid, username, label);
    }

    public synchronized Result join(String gameId, String uuid, String username, String label) {
        Game game = games.get(clean(gameId));
        uuid = userKey(uuid); username = plain(username); label = plain(label);
        if (game == null) return fail("game_not_found", uuid);
        if (game.type.equals("poll")) return fail(game, "vote_required", uuid);
        if (game.type.equals("recruitment")) return fail(game, "role_required", uuid);
        if (!game.status.equals("open")) return fail(game, "game_not_open", uuid);
        if (!validUser(uuid, username)) return fail(game, "invalid_user", uuid);
        if (game.participants.containsKey(uuid)) return new Result(true, "already_joined", snapshotMap(game, uuid), false);
        if (game.participants.size() >= game.maxParticipants) return fail(game, "game_full", uuid);
        putIdentity(game, uuid, username, label);
        if (game.type.equals("firstcome") && game.winners.size() < game.winnerCount) game.winners.add(uuid);
        if (!applyCountAutomaticEnd(game)) {
            if (game.type.equals("lottery") && game.participants.size() >= game.maxParticipants) game.status = "ready";
        }
        save(game);
        return changed(game, uuid);
    }

    public synchronized Result vote(String uuid, String username, String label, int optionIndex) {
        Game game = defaultOpenGameOfType("poll");
        return game == null ? fail("game_not_found", uuid) : vote(game.id, uuid, username, label, optionIndex);
    }

    public synchronized Result vote(String gameId, String uuid, String username, String label, int optionIndex) {
        Game game = games.get(clean(gameId));
        uuid = userKey(uuid); username = plain(username); label = plain(label);
        if (game == null) return fail("game_not_found", uuid);
        if (!game.type.equals("poll")) return fail(game, "not_poll", uuid);
        if (!game.status.equals("open")) return fail(game, "game_not_open", uuid);
        if (!validUser(uuid, username)) return fail(game, "invalid_user", uuid);
        if (optionIndex < 1 || optionIndex > game.pollOptions.size()) return fail(game, "invalid_poll_option", uuid);
        Integer old = game.pollVotes.get(uuid);
        putIdentity(game, uuid, username, label);
        game.pollVotes.put(uuid, optionIndex);
        if (old != null && old == optionIndex) return new Result(true, "already_voted", snapshotMap(game, uuid), false);
        applyCountAutomaticEnd(game);
        save(game);
        return changed(game, uuid);
    }

    public synchronized Result applyRecruitment(String uuid, String username, String label, String role) {
        Game game = defaultOpenGameOfType("recruitment");
        return game == null ? fail("game_not_found", uuid) : applyRecruitment(game.id, uuid, username, label, role);
    }

    public synchronized Result applyRecruitment(String gameId, String uuid, String username, String label, String requestedRole) {
        Game game = games.get(clean(gameId));
        uuid = userKey(uuid); username = plain(username); label = plain(label);
        if (game == null) return fail("game_not_found", uuid);
        if (!game.type.equals("recruitment")) return fail(game, "not_recruitment", uuid);
        if (!game.status.equals("open")) return fail(game, "game_not_open", uuid);
        if (!validUser(uuid, username)) return fail(game, "invalid_user", uuid);
        String role = canonicalRole(game, requestedRole);
        if (role.isBlank()) return fail(game, "invalid_recruitment_role", uuid);

        String oldRole = game.participantRoles.getOrDefault(uuid, "");
        String oldState = game.participantStates.getOrDefault(uuid, "");
        if (role.equals(oldRole)) {
            putIdentity(game, uuid, username, label);
            return new Result(true, "waiting".equals(oldState) ? "already_waiting" : "already_applied", snapshotMap(game, uuid), false);
        }

        // Switching roles releases an accepted slot first and promotes the oldest waiter.
        if (!oldRole.isBlank()) {
            game.participantRoles.remove(uuid);
            game.participantStates.remove(uuid);
            game.participants.remove(uuid);
            game.participantUsernames.remove(uuid);
            if ("accepted".equals(oldState)) promoteNextWaiting(game, oldRole);
        }

        putIdentity(game, uuid, username, label);
        game.participantRoles.put(uuid, role);
        String state = acceptedCount(game, role) < game.recruitmentRoles.getOrDefault(role, 0) ? "accepted" : "waiting";
        game.participantStates.put(uuid, state);
        applyCountAutomaticEnd(game);
        save(game);
        return new Result(true, state, snapshotMap(game, uuid), true);
    }

    public synchronized Result withdrawRecruitment(String uuid) {
        Game game = defaultOpenGameOfType("recruitment");
        return game == null ? fail("game_not_found", uuid) : withdrawRecruitment(game.id, uuid);
    }

    public synchronized Result withdrawRecruitment(String gameId, String uuid) {
        Game game = games.get(clean(gameId));
        uuid = userKey(uuid);
        if (game == null) return fail("game_not_found", uuid);
        if (!game.type.equals("recruitment")) return fail(game, "not_recruitment", uuid);
        if (!game.status.equals("open")) return fail(game, "game_not_open", uuid);
        String role = game.participantRoles.getOrDefault(uuid, "");
        if (role.isBlank()) return fail(game, "not_applied", uuid);
        String state = game.participantStates.getOrDefault(uuid, "");
        game.participantRoles.remove(uuid);
        game.participantStates.remove(uuid);
        game.participants.remove(uuid);
        game.participantUsernames.remove(uuid);
        if ("accepted".equals(state)) promoteNextWaiting(game, role);
        save(game);
        return changed(game, uuid);
    }

    public synchronized Result draw() {
        Game game = defaultManageableGameOfType("lottery");
        return game == null ? fail("game_not_found", "") : draw(game.id);
    }

    public synchronized Result draw(String gameId) {
        Game game = games.get(clean(gameId));
        if (game == null) return fail("game_not_found", "");
        if (!game.type.equals("lottery")) return fail(game, "not_lottery", "");
        if (!game.status.equals("open") && !game.status.equals("ready")) return fail(game, "game_not_open", "");
        if (game.participants.size() < game.winnerCount) return fail(game, "not_enough_participants", "");
        finalizeGame(game, "manual");
        save(game);
        return changed(game, "");
    }

    /** Ends registration/response immediately. Lottery additionally draws from collected participants. */
    public synchronized Result finish() {
        Game game = defaultManageableGame();
        return game == null ? fail("game_not_found", "") : finish(game.id);
    }

    public synchronized Result finish(String gameId) {
        Game game = games.get(clean(gameId));
        if (game == null) return fail("game_not_found", "");
        if (!game.status.equals("open") && !game.status.equals("ready")) return fail(game, "game_not_open", "");
        finalizeGame(game, "manual");
        save(game);
        return changed(game, "");
    }

    /** Updates event automatic-end policy without changing participant/response state. */
    public synchronized Result setAutoEnd(String gameId, boolean autoEndOnCapacity, int autoEndResponseCount, long autoEndAt) {
        Game game = games.get(clean(gameId));
        if (game == null) return fail("game_not_found", "");
        if (!("open".equals(game.status) || "ready".equals(game.status))) return fail(game, "game_not_open", "");
        if (autoEndResponseCount < 0 || autoEndResponseCount > 500) return fail(game, "invalid_auto_end_count", "");
        if (!validAutoEndAt(autoEndAt)) return fail(game, "invalid_auto_end_time", "");
        if ("firstcome".equals(game.type)) {
            game.autoEndOnCapacity = true;
            game.autoEndResponseCount = 0;
        } else if ("lottery".equals(game.type) || "recruitment".equals(game.type)) {
            game.autoEndOnCapacity = autoEndOnCapacity;
            game.autoEndResponseCount = 0;
        } else if ("poll".equals(game.type)) {
            game.autoEndOnCapacity = false;
            game.autoEndResponseCount = autoEndResponseCount;
        }
        game.autoEndAt = autoEndAt;
        boolean completed = applyCountAutomaticEnd(game);
        save(game);
        return changed(game, "");
    }

    /** Updates whether this event's system announcements may be sent through Relay. */
    public synchronized Result setRelayAnnouncements(String gameId, boolean relayAnnouncements) {
        Game game = games.get(clean(gameId));
        if (game == null) return fail("game_not_found", "");
        if (!("open".equals(game.status) || "ready".equals(game.status))) return fail(game, "game_not_open", "");
        game.relayAnnouncements = relayAnnouncements;
        save(game);
        return changed(game, "");
    }

    /** Finalizes events whose configured absolute end time has arrived. */
    public synchronized List<Result> finishDue(long now) {
        List<Result> changed = new ArrayList<>();
        for (Game game : games.values()) {
            if (!("open".equals(game.status) || "ready".equals(game.status))) continue;
            if (game.autoEndAt <= 0L || now < game.autoEndAt) continue;
            finalizeGame(game, "time");
            save(game);
            changed.add(changed(game, ""));
        }
        return changed;
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

    private static boolean validAutoEndAt(long autoEndAt) {
        return autoEndAt == 0L || autoEndAt > System.currentTimeMillis();
    }

    private boolean applyCountAutomaticEnd(Game game) {
        if (game == null || !("open".equals(game.status) || "ready".equals(game.status))) return false;
        if ("firstcome".equals(game.type) && game.winners.size() >= game.winnerCount) {
            finalizeGame(game, "capacity");
            return true;
        }
        if ("lottery".equals(game.type) && game.autoEndOnCapacity && game.maxParticipants > 0 && game.participants.size() >= game.maxParticipants) {
            finalizeGame(game, "capacity");
            return true;
        }
        if ("poll".equals(game.type) && game.autoEndResponseCount > 0 && game.pollVotes.size() >= game.autoEndResponseCount) {
            finalizeGame(game, "response_count");
            return true;
        }
        if ("recruitment".equals(game.type) && game.autoEndOnCapacity && recruitmentCapacityFilled(game)) {
            finalizeGame(game, "capacity");
            return true;
        }
        return false;
    }

    private static boolean recruitmentCapacityFilled(Game game) {
        if (game == null || game.recruitmentRoles.isEmpty()) return false;
        for (Map.Entry<String,Integer> role : game.recruitmentRoles.entrySet()) {
            if (acceptedCount(game, role.getKey()) < Math.max(0, role.getValue() == null ? 0 : role.getValue())) return false;
        }
        return true;
    }

    private void finalizeGame(Game game, String reason) {
        if (game == null) return;
        if ("lottery".equals(game.type)) {
            List<String> candidates = new ArrayList<>(game.participants.keySet());
            Collections.shuffle(candidates, random);
            game.winners.clear();
            game.winners.addAll(candidates.subList(0, Math.min(game.winnerCount, candidates.size())));
        }
        game.status = "completed";
        game.completionReason = clean(reason);
        game.completedAt = System.currentTimeMillis();
    }

    private Game newGame(String type, String title, String createdBy, boolean relayAnnouncements) {
        Game next = new Game();
        next.id = UUID.randomUUID().toString();
        next.type = type;
        next.title = title;
        next.relayAnnouncements = relayAnnouncements;
        next.createdBy = plain(createdBy);
        next.createdAt = System.currentTimeMillis();
        return next;
    }

    private Result addNewGame(Game next) {
        games.put(next.id, next);
        save(next);
        return changed(next, "");
    }

    private static boolean validTitle(String title) {
        return title != null && !title.isBlank() && title.codePointCount(0, title.length()) <= 80;
    }

    private static boolean validUser(String uuid, String username) { return !clean(uuid).isBlank() && !clean(username).isBlank(); }

    private static void putIdentity(Game game, String uuid, String username, String label) {
        game.participants.put(uuid, label.isBlank() ? username : label);
        game.participantUsernames.put(uuid, username);
    }

    private static String userKey(String uuid) { return clean(uuid).toLowerCase(Locale.ROOT); }

    private static List<String> parsePollOptions(String raw) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String item : clean(raw).split("\\|")) {
            String option = plain(item);
            if (option.isBlank() || option.codePointCount(0, option.length()) > 60) continue;
            String key = option.toLowerCase(Locale.ROOT);
            if (seen.add(key)) out.add(option);
            if (out.size() >= 20) break;
        }
        return out;
    }

    private static LinkedHashMap<String,Integer> parseRecruitmentRoles(String raw) {
        LinkedHashMap<String,Integer> out = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String item : clean(raw).split("\\|")) {
            int split = item.lastIndexOf(':');
            if (split <= 0) continue;
            String role = plain(item.substring(0, split));
            int capacity = integer(item.substring(split + 1), -1);
            if (role.isBlank() || role.codePointCount(0, role.length()) > 40 || capacity < 1 || capacity > 500) continue;
            String key = role.toLowerCase(Locale.ROOT);
            if (seen.add(key)) out.put(role, capacity);
            if (out.size() >= 20) break;
        }
        return out;
    }

    private static String canonicalRole(Game game, String requested) {
        String raw = plain(requested);
        for (String role : game.recruitmentRoles.keySet()) if (role.equalsIgnoreCase(raw)) return role;
        return "";
    }

    private static int acceptedCount(Game game, String role) {
        int count = 0;
        for (Map.Entry<String,String> entry : game.participantRoles.entrySet()) {
            if (!role.equals(entry.getValue())) continue;
            if ("accepted".equals(game.participantStates.get(entry.getKey()))) count++;
        }
        return count;
    }

    private static int waitingCount(Game game, String role) {
        int count = 0;
        for (Map.Entry<String,String> entry : game.participantRoles.entrySet()) {
            if (role.equals(entry.getValue()) && "waiting".equals(game.participantStates.get(entry.getKey()))) count++;
        }
        return count;
    }

    private static int waitingPosition(Game game, String uuid, String role) {
        int position = 0;
        for (String participant : game.participants.keySet()) {
            if (!role.equals(game.participantRoles.get(participant))) continue;
            if (!"waiting".equals(game.participantStates.get(participant))) continue;
            position++;
            if (participant.equals(uuid)) return position;
        }
        return 0;
    }

    private static void promoteNextWaiting(Game game, String role) {
        int capacity = game.recruitmentRoles.getOrDefault(role, 0);
        if (capacity <= 0 || acceptedCount(game, role) >= capacity) return;
        for (String uuid : game.participants.keySet()) {
            if (role.equals(game.participantRoles.get(uuid)) && "waiting".equals(game.participantStates.get(uuid))) {
                game.participantStates.put(uuid, "accepted");
                return;
            }
        }
    }

    private Game defaultGame() {
        Game active = defaultManageableGame();
        if (active != null) return active;
        return games.values().stream().max(Comparator.comparingLong(g -> g.createdAt)).orElse(null);
    }

    private Game defaultJoinableGame() {
        return games.values().stream().filter(g -> "open".equals(g.status) && ("firstcome".equals(g.type) || "lottery".equals(g.type)))
                .max(Comparator.comparingLong(g -> g.createdAt)).orElse(null);
    }

    private Game defaultOpenGameOfType(String type) {
        return games.values().stream().filter(g -> "open".equals(g.status) && type.equals(g.type))
                .max(Comparator.comparingLong(g -> g.createdAt)).orElse(null);
    }

    private Game defaultManageableGame() {
        return games.values().stream().filter(g -> "open".equals(g.status) || "ready".equals(g.status))
                .max(Comparator.comparingLong(g -> g.createdAt)).orElse(null);
    }

    private Game defaultManageableGameOfType(String type) {
        return games.values().stream().filter(g -> type.equals(g.type) && ("open".equals(g.status) || "ready".equals(g.status)))
                .max(Comparator.comparingLong(g -> g.createdAt)).orElse(null);
    }

    private Result changed(Game game, String viewerUuid) { return new Result(true, "", snapshotMap(game, viewerUuid), true); }
    private Result fail(String error, String viewerUuid) { return new Result(false, error, snapshotMap(defaultGame(), viewerUuid), false); }
    private Result fail(Game game, String error, String viewerUuid) { return new Result(false, error, snapshotMap(game, viewerUuid), false); }

    private Map<String,Object> snapshotMap(Game game, String viewerUuid) {
        if (game == null) return null;
        String viewer = userKey(viewerUuid);
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
        out.put("autoEndOnCapacity", game.autoEndOnCapacity);
        out.put("autoEndResponseCount", game.autoEndResponseCount);
        out.put("autoEndAt", game.autoEndAt);
        out.put("completionReason", game.completionReason);
        out.put("completedAt", game.completedAt);
        out.put("joined", game.participants.containsKey(viewer));

        List<Map<String,Object>> participants = new ArrayList<>();
        for (Map.Entry<String,String> entry : game.participants.entrySet()) {
            LinkedHashMap<String,Object> participant = participantIdentityMap(game, entry.getKey(), entry.getValue());
            if (game.type.equals("recruitment")) {
                String role = game.participantRoles.getOrDefault(entry.getKey(), "");
                String state = game.participantStates.getOrDefault(entry.getKey(), "");
                participant.put("role", role);
                participant.put("state", state);
                participant.put("queuePosition", "waiting".equals(state) ? waitingPosition(game, entry.getKey(), role) : 0);
            }
            participants.add(participant);
        }
        out.put("participants", participants);

        List<Map<String,Object>> winners = new ArrayList<>();
        for (String uuid : game.winners) winners.add(winnerIdentityMap(game, uuid, game.participants.getOrDefault(uuid, uuid)));
        out.put("winners", winners);

        if (game.type.equals("poll")) {
            List<Map<String,Object>> options = new ArrayList<>();
            for (int i = 0; i < game.pollOptions.size(); i++) {
                int index = i + 1;
                int count = 0;
                for (Integer selected : game.pollVotes.values()) if (selected != null && selected == index) count++;
                LinkedHashMap<String,Object> option = new LinkedHashMap<>();
                option.put("index", index);
                option.put("label", game.pollOptions.get(i));
                option.put("count", count);
                options.add(option);
            }
            out.put("pollOptions", options);
            out.put("pollSelectedOption", game.pollVotes.getOrDefault(viewer, 0));
            out.put("voteCount", game.pollVotes.size());
        }

        if (game.type.equals("recruitment")) {
            List<Map<String,Object>> roles = new ArrayList<>();
            int acceptedTotal = 0;
            int waitingTotal = 0;
            for (Map.Entry<String,Integer> entry : game.recruitmentRoles.entrySet()) {
                int accepted = acceptedCount(game, entry.getKey());
                int waiting = waitingCount(game, entry.getKey());
                acceptedTotal += accepted;
                waitingTotal += waiting;
                LinkedHashMap<String,Object> role = new LinkedHashMap<>();
                role.put("name", entry.getKey());
                role.put("capacity", entry.getValue());
                role.put("accepted", accepted);
                role.put("waiting", waiting);
                roles.add(role);
            }
            out.put("recruitmentRoles", roles);
            out.put("acceptedCount", acceptedTotal);
            out.put("waitingCount", waitingTotal);
            String role = game.participantRoles.getOrDefault(viewer, "");
            String state = game.participantStates.getOrDefault(viewer, "");
            out.put("recruitmentRole", role);
            out.put("recruitmentState", state);
            out.put("recruitmentQueuePosition", "waiting".equals(state) ? waitingPosition(game, viewer, role) : 0);
        }
        return out;
    }

    private static LinkedHashMap<String,Object> participantIdentityMap(Game game, String uuid, String displayName) {
        LinkedHashMap<String,Object> participant = new LinkedHashMap<>();
        participant.put("uuid", uuid);
        participant.put("label", displayName);
        participant.put("displayName", displayName);
        participant.put("username", game.participantUsernames.getOrDefault(uuid, ""));
        return participant;
    }

    private static LinkedHashMap<String,Object> winnerIdentityMap(Game game, String uuid, String displayName) {
        LinkedHashMap<String,Object> winner = new LinkedHashMap<>();
        winner.put("uuid", uuid);
        winner.put("label", displayName);
        winner.put("displayName", displayName);
        winner.put("username", game.participantUsernames.getOrDefault(uuid, ""));
        return winner;
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
        p.setProperty("autoEndOnCapacity", String.valueOf(game.autoEndOnCapacity));
        p.setProperty("autoEndResponseCount", String.valueOf(game.autoEndResponseCount));
        p.setProperty("autoEndAt", String.valueOf(game.autoEndAt));
        p.setProperty("completionReason", game.completionReason);
        p.setProperty("completedAt", String.valueOf(game.completedAt));
        p.setProperty("participants", encodeStringMap(game.participants));
        p.setProperty("participantUsernames", encodeStringMap(game.participantUsernames));
        p.setProperty("winners", String.join(",", game.winners));
        if (!game.pollOptions.isEmpty()) p.setProperty("pollOptions", encodeStringList(game.pollOptions));
        if (!game.pollVotes.isEmpty()) p.setProperty("pollVotes", encodeIntMap(game.pollVotes));
        if (!game.recruitmentRoles.isEmpty()) p.setProperty("recruitmentRoles", encodeIntMap(game.recruitmentRoles));
        if (!game.participantRoles.isEmpty()) p.setProperty("participantRoles", encodeStringMap(game.participantRoles));
        if (!game.participantStates.isEmpty()) p.setProperty("participantStates", encodeStringMap(game.participantStates));
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
            loaded.id = clean(p.getProperty("id", ""));
            loaded.type = clean(p.getProperty("type", "lottery")).toLowerCase(Locale.ROOT);
            loaded.title = plain(dec(p.getProperty("title", "")));
            loaded.maxParticipants = integer(p.getProperty("maxParticipants"), 0);
            loaded.winnerCount = integer(p.getProperty("winnerCount"), 0);
            if ("firstcome".equals(loaded.type) && loaded.winnerCount > 0) loaded.maxParticipants = loaded.winnerCount;
            loaded.relayAnnouncements = Boolean.parseBoolean(p.getProperty("relayAnnouncements", "true"));
            loaded.status = p.getProperty("status", "closed");
            loaded.createdBy = plain(dec(p.getProperty("createdBy", "")));
            loaded.createdAt = longValue(p.getProperty("createdAt"));
            loaded.autoEndOnCapacity = Boolean.parseBoolean(p.getProperty("autoEndOnCapacity", "false"));
            if ("firstcome".equals(loaded.type)) loaded.autoEndOnCapacity = true;
            loaded.autoEndResponseCount = Math.max(0, integer(p.getProperty("autoEndResponseCount"), 0));
            loaded.autoEndAt = Math.max(0L, longValue(p.getProperty("autoEndAt")));
            loaded.completionReason = clean(p.getProperty("completionReason", ""));
            loaded.completedAt = Math.max(0L, longValue(p.getProperty("completedAt")));
            decodeStringMap(p.getProperty("participants", ""), loaded.participants);
            decodeStringMap(p.getProperty("participantUsernames", ""), loaded.participantUsernames);
            if (!loaded.participantUsernames.keySet().equals(loaded.participants.keySet())) return null;
            for (String winner : p.getProperty("winners", "").split(",")) if (!clean(winner).isBlank()) loaded.winners.add(clean(winner));

            if ("poll".equals(loaded.type)) {
                decodeStringList(p.getProperty("pollOptions", ""), loaded.pollOptions);
                decodeIntMap(p.getProperty("pollVotes", ""), loaded.pollVotes);
                loaded.pollVotes.keySet().retainAll(loaded.participants.keySet());
                if (loaded.pollOptions.size() < 2) return null;
                loaded.maxParticipants = 0;
                loaded.winnerCount = 0;
            } else if ("recruitment".equals(loaded.type)) {
                decodeIntMap(p.getProperty("recruitmentRoles", ""), loaded.recruitmentRoles);
                decodeStringMap(p.getProperty("participantRoles", ""), loaded.participantRoles);
                decodeStringMap(p.getProperty("participantStates", ""), loaded.participantStates);
                loaded.participantRoles.keySet().retainAll(loaded.participants.keySet());
                loaded.participantStates.keySet().retainAll(loaded.participants.keySet());
                if (loaded.recruitmentRoles.isEmpty()) return null;
                int total = 0; for (Integer v : loaded.recruitmentRoles.values()) total += Math.max(0, v == null ? 0 : v);
                loaded.maxParticipants = total;
                loaded.winnerCount = 0;
                for (String uuid : new ArrayList<>(loaded.participants.keySet())) {
                    String role = canonicalRole(loaded, loaded.participantRoles.get(uuid));
                    String state = loaded.participantStates.getOrDefault(uuid, "");
                    if (role.isBlank() || (!"accepted".equals(state) && !"waiting".equals(state))) {
                        loaded.participants.remove(uuid); loaded.participantUsernames.remove(uuid);
                        loaded.participantRoles.remove(uuid); loaded.participantStates.remove(uuid);
                    } else loaded.participantRoles.put(uuid, role);
                }
            } else if ("firstcome".equals(loaded.type) || "lottery".equals(loaded.type)) {
                if (loaded.maxParticipants <= 0 || loaded.winnerCount <= 0) return null;
                if ("firstcome".equals(loaded.type) && ("open".equals(loaded.status) || "ready".equals(loaded.status))
                        && loaded.winners.size() >= loaded.winnerCount) loaded.status = "completed";
            } else return null;

            if (("open".equals(loaded.status) || "ready".equals(loaded.status))) {
                if (loaded.autoEndAt > 0L && System.currentTimeMillis() >= loaded.autoEndAt) finalizeGame(loaded, "time");
                else applyCountAutomaticEnd(loaded);
                if ("completed".equals(loaded.status)) save(loaded);
            }
            if (!loaded.id.isBlank() && validTitle(loaded.title)) return loaded;
        } catch (Exception ignored) {}
        return null;
    }

    private static String encodeStringMap(Map<String,String> values) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String,String> entry : values.entrySet()) out.add(enc(entry.getKey()) + ":" + enc(entry.getValue()));
        return String.join(";", out);
    }
    private static void decodeStringMap(String raw, Map<String,String> out) {
        for (String item : clean(raw).split(";")) {
            int split = item.indexOf(':');
            if (split > 0) out.put(dec(item.substring(0, split)), plain(dec(item.substring(split + 1))));
        }
    }
    private static String encodeIntMap(Map<String,Integer> values) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String,Integer> entry : values.entrySet()) out.add(enc(entry.getKey()) + ":" + Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
        return String.join(";", out);
    }
    private static void decodeIntMap(String raw, Map<String,Integer> out) {
        for (String item : clean(raw).split(";")) {
            int split = item.lastIndexOf(':');
            if (split > 0) {
                String key = dec(item.substring(0, split));
                int value = integer(item.substring(split + 1), -1);
                if (!key.isBlank() && value >= 0) out.put(key, value);
            }
        }
    }
    private static String encodeStringList(List<String> values) {
        List<String> out = new ArrayList<>();
        for (String value : values) out.add(enc(value));
        return String.join(";", out);
    }
    private static void decodeStringList(String raw, List<String> out) {
        for (String item : clean(raw).split(";")) {
            String value = plain(dec(item));
            if (!value.isBlank()) out.add(value);
        }
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
