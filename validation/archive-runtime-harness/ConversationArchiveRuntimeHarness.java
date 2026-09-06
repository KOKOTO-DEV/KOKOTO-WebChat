import dev.kokoto.webchat.ConversationArchiveStore;
import dev.kokoto.webchat.CoreLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runtime smoke test executed against a finished shaded KWC JAR. */
public final class ConversationArchiveRuntimeHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("kwc-archive-runtime-");
        CoreLogger logger = new CoreLogger() {
            @Override public void info(String message) { }
            @Override public void warn(String message) { System.err.println("[archive-harness] " + message); }
        };

        try {
            try (ConversationArchiveStore store = new ConversationArchiveStore(temp, logger, 3, 3, 5)) {
                store.open();
            check(store.available(), "archive store unavailable; sqlite-jdbc may be missing from the release JAR");
            check(store.maxArchivesPerUser() == 3, "configured archive-count quota was not applied");
            check(store.maxMessagesPerArchive() == 3, "configured per-archive message quota was not applied");
            check(store.maxMessagesPerUser() == 5, "configured per-account message quota was not applied");

            List<ConversationArchiveStore.SnapshotMessage> messages = new ArrayList<>();
            messages.add(message("m1", 1000L, "11111111-1111-1111-1111-111111111111", "Alice", "Alice Display", "one"));
            messages.add(message("m2", 2000L, "22222222-2222-2222-2222-222222222222", "Bob", "Bob Display", "two"));
            messages.add(message("m3", 3000L, "11111111-1111-1111-1111-111111111111", "Alice", "Alice Display", "three"));

            ConversationArchiveStore.SaveResult saved = store.save(
                    "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA", "dm", "thread-1", "Runtime smoke", messages);
            check(saved.ok && saved.archive != null, "save failed: " + saved.error);
            String archiveId = saved.archive.id;
            check(store.list("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 10).size() == 1, "list did not return saved archive");

            ConversationArchiveStore.Archive loaded = store.get("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", archiveId);
            check(loaded != null && loaded.messages.size() == 3, "get did not return the full snapshot");
            check(loaded.toJson().contains("\"senderUsername\":\"Alice\""), "real username missing from browser projection");
            check(loaded.toJson().contains("\"senderDisplayName\":\"Alice Display\""), "display name missing from browser projection");
            check(!loaded.toJson().contains("11111111-1111-1111-1111-111111111111"), "sender UUID leaked into browser projection");

            check(store.rename("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", archiveId, "Renamed"), "rename failed");
            check("Renamed".equals(store.get("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", archiveId).title), "renamed title not persisted");

            check(store.removeSourceMessage("dm", "thread-1", "m2") == 1, "source-message cascade did not remove exactly one row");
            loaded = store.get("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", archiveId);
            check(loaded != null && loaded.messageCount == 2 && loaded.messages.size() == 2, "archive metadata not refreshed after source-message delete");
            check(loaded.firstMessageAt == 1000L && loaded.lastMessageAt == 3000L, "archive range metadata changed incorrectly");

            List<ConversationArchiveStore.SnapshotMessage> tooMany = new ArrayList<>();
            for (int i = 0; i <= store.maxMessagesPerArchive(); i++) {
                tooMany.add(message("q" + i, 4000L + i, "", "User", "User", "quota"));
            }
            ConversationArchiveStore.SaveResult rejected = store.save(
                    "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "public", "public", "Too many", tooMany);
            check(!rejected.ok && "range_too_large".equals(rejected.error), "configured per-archive message quota was not enforced");

            List<ConversationArchiveStore.SnapshotMessage> three = List.of(
                    message("u1", 5001L, "", "User", "User", "one"),
                    message("u2", 5002L, "", "User", "User", "two"),
                    message("u3", 5003L, "", "User", "User", "three"));
            ConversationArchiveStore.SaveResult fillsUserQuota = store.save(
                    "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "public", "public", "Fills user quota", three);
            check(fillsUserQuota.ok, "save at configured per-account message quota failed: " + fillsUserQuota.error);
            ConversationArchiveStore.SaveResult userQuotaRejected = store.save(
                    "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "public", "public", "Over user quota",
                    List.of(message("u4", 5004L, "", "User", "User", "four")));
            check(!userQuotaRejected.ok && "archive_message_quota".equals(userQuotaRejected.error), "configured per-account message quota was not enforced");

            String ownerB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
            for (int i = 0; i < store.maxArchivesPerUser(); i++) {
                ConversationArchiveStore.SaveResult r = store.save(ownerB, "public", "public", "Archive " + i,
                        List.of(message("b" + i, 6000L + i, "", "User", "User", "archive")));
                check(r.ok, "save before configured archive-count quota failed: " + r.error);
            }
            ConversationArchiveStore.SaveResult archiveQuotaRejected = store.save(ownerB, "public", "public", "Over archive quota",
                    List.of(message("b-over", 7000L, "", "User", "User", "archive")));
            check(!archiveQuotaRejected.ok && "archive_quota".equals(archiveQuotaRejected.error), "configured archive-count quota was not enforced");

            check(store.removeSource("dm", "thread-1") == 1, "source delete did not remove the archive");
            check(store.get("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", archiveId) == null, "archive survived administrator source delete");
            }

            // Lowering an operator quota must not delete or hide snapshots that already exist.
            try (ConversationArchiveStore lowered = new ConversationArchiveStore(temp, logger, 2, 3, 5)) {
                lowered.open();
                check(lowered.available(), "archive store unavailable after reopening with lower quota");
                check(lowered.list("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 1000).size() == 3,
                        "lowering archive quota hid existing snapshots");
                ConversationArchiveStore.SaveResult blocked = lowered.save(
                        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "public", "public", "Still over quota",
                        List.of(message("b-lowered", 8000L, "", "User", "User", "archive")));
                check(!blocked.ok && "archive_quota".equals(blocked.error),
                        "lowered archive quota did not block new saves while existing count exceeded it");
            }
        } finally {
            deleteRecursively(temp);
        }

        System.out.println("CONVERSATION_ARCHIVE_RUNTIME_HARNESS_PASS assertions=" + checks);
    }

    private static ConversationArchiveStore.SnapshotMessage message(String id, long time, String uuid,
                                                                     String username, String display, String body) {
        ConversationArchiveStore.SnapshotMessage message = new ConversationArchiveStore.SnapshotMessage();
        message.sourceMessageId = id;
        message.time = time;
        message.senderUuid = uuid;
        message.senderUsername = username;
        message.senderDisplayName = display;
        message.body = body;
        message.messageSource = "web";
        return message;
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
