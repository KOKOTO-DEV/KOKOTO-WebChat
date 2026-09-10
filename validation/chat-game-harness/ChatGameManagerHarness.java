import dev.kokoto.webchat.ChatGameManager;

/* KWC 파일 안내 / KWC file guide
 * RC26 harness: 다중 이벤트, ID별 작업, 참가/추첨, 재시작 복구를 검증한다.
 * RC26 harness: verifies multiple events, ID-addressed actions, participation/draw, and restart recovery.
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChatGameManagerHarness {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("kwc-chat-game-");
        ChatGameManager games = new ChatGameManager(root);
        check(games.snapshot("").game() == null, "new store is empty");
        check(!games.create("bad", "Event", 3, 1, "admin").ok(), "invalid type rejected");
        ChatGameManager.Result normalized = games.create("firstcome", "Normalized first come", 2, 3, "admin");
        check(normalized.ok() && Integer.valueOf(3).equals(normalized.game().get("maxParticipants")), "first-come capacity is normalized to winner count");
        check(Integer.valueOf(3).equals(normalized.game().get("winnerCount")), "first-come winner count is authoritative capacity");
        games.delete(String.valueOf(normalized.game().get("id")));

        ChatGameManager.Result createdA = games.create("firstcome", "Fast two", 20, 2, "admin");
        Thread.sleep(2L); // createdAt uses millisecond precision; keep the ordering assertion deterministic.
        ChatGameManager.Result createdB = games.create("lottery", "Lucky one", 4, 1, "admin", false);
        check(createdA.ok() && createdB.ok(), "two events can exist at once");
        String idA = String.valueOf(createdA.game().get("id"));
        String idB = String.valueOf(createdB.game().get("id"));
        check(!idA.equals(idB), "events have unique ids");
        check(games.list("").size() == 2, "event list exposes both events");
        check("Lucky one".equals(games.list("").get(0).get("title")), "newest event sorts first");
        check(Boolean.FALSE.equals(games.snapshot(idB, "").game().get("relayAnnouncements")), "per-event relay scope preserved");

        check(games.join(idA, "A", "Alice").ok(), "first event player joined");
        ChatGameManager.Result duplicate = games.join(idA, "a", "Alice 2");
        check(duplicate.ok() && "already_joined".equals(duplicate.error()) && !duplicate.changed(), "join is idempotent per event");
        check(games.join(idB, "A", "Alice").ok(), "same player may join another event");
        ChatGameManager.Result fullA = games.join(idA, "B", "Bob");
        check(fullA.ok() && "completed".equals(fullA.game().get("status")), "first-come completes immediately when all winner slots fill");
        check(Integer.valueOf(2).equals(fullA.game().get("maxParticipants")), "separate first-come participant capacity is not retained");
        check(labels(fullA.game(), "winners").equals(Set.of("Alice", "Bob")), "first arrivals win");
        ChatGameManager.Result lateA = games.join(idA, "C", "Carol");
        check(!lateA.ok() && "game_not_open".equals(lateA.error()), "late first-come joins are rejected after automatic completion");
        check("open".equals(games.snapshot(idB, "").game().get("status")), "other event remains independently open");

        games.join(idB, "D", "Dana");
        games.join(idB, "E", "Evan");
        games.join(idB, "F", "Fran");
        check("ready".equals(games.snapshot(idB, "").game().get("status")), "full lottery becomes ready");
        ChatGameManager.Result drawn = games.draw(idB);
        check(drawn.ok() && "completed".equals(drawn.game().get("status")), "selected lottery drawn");
        check(labels(drawn.game(), "winners").size() == 1, "exact winner count drawn");
        check(labels(drawn.game(), "participants").containsAll(labels(drawn.game(), "winners")), "winner is participant");

        ChatGameManager.Result early = games.create("lottery", "Early lottery", 10, 3, "§x§b§7§a§e§b§c§lAdmin");
        String idEarly = String.valueOf(early.game().get("id"));
        games.join(idEarly, "G", "**§x§b§7§a§e§b§c§lGina**");
        ChatGameManager.Result earlyFinished = games.finish(idEarly);
        check(earlyFinished.ok() && "completed".equals(earlyFinished.game().get("status")), "selected lottery can finish early");
        check(labels(earlyFinished.game(), "participants").contains("Gina"), "legacy color tags removed from participant names");
        check(labels(earlyFinished.game(), "winners").equals(Set.of("Gina")), "early lottery draws available participants only");

        ChatGameManager restored = new ChatGameManager(root);
        check(restored.list("").size() == 3, "all events restored from disk");
        check("Fast two".equals(restored.snapshot(idA, "b").game().get("title")), "event A restored by id");
        check(Boolean.TRUE.equals(restored.snapshot(idA, "b").game().get("joined")), "viewer join state is event-specific after restart");
        check(Boolean.FALSE.equals(restored.snapshot(idB, "").game().get("relayAnnouncements")), "announcement scope restored from disk");
        check(!restored.snapshot("missing-id", "").ok(), "unknown event id is rejected");

        ChatGameManager.Result deleted = restored.delete(idA);
        check(deleted.ok() && deleted.changed(), "explicit event deletion succeeds");
        check(restored.list("").size() == 2, "deleted event disappears from retained list");
        check(!restored.snapshot(idA, "").ok(), "deleted event cannot be reopened");
        ChatGameManager afterDeleteRestart = new ChatGameManager(root);
        check(afterDeleteRestart.list("").size() == 2, "event deletion persists across restart");

        long files;
        try (var stream = Files.list(root.resolve("chat-games"))) { files = stream.filter(p -> p.getFileName().toString().endsWith(".properties")).count(); }
        check(files == 2, "deleted event properties file is removed while other history remains");
        System.out.println("CHAT_GAME_MANAGER_HARNESS_PASS assertions=" + assertions);
    }

    private static Set<String> labels(Map<String,Object> game, String key) {
        Set<String> out = new HashSet<>();
        Object raw = game.get(key);
        if (raw instanceof List<?> values) for (Object value : values) {
            if (value instanceof Map<?,?> item) out.add(String.valueOf(item.get("label")));
        }
        return out;
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
