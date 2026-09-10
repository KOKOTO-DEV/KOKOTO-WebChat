package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * PrivateSearchHarness는 validation 모듈의 KWC 구현 파일이다. 클래스 이름이 나타내는 책임을 이 파일 안에 한정해 다른 계층과의 결합을 줄인다.
 * PrivateSearchHarness is a KWC implementation file in the validation module. Keep the responsibility implied by the class name localized here to reduce cross-layer coupling.
 *
 * 변경 시 호출자와 반환값뿐 아니라 인증/권한, thread context, persistence, multi-loader 호환성에 미치는 영향을 함께 확인한다.
 * When changing it, review not only callers/returns but also effects on authorization, thread context, persistence, and multi-loader compatibility.
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PrivateSearchHarness {
    private static int checks = 0;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static final class Host implements ConversationStoreHost {
        final Path dir;
        final Map<String, PlayerIdentity> identities = new HashMap<>();
        final DirectMessageSettings dm;
        final GroupChatSettings group;

        Host(Path dir) {
            this.dir = dir;
            dm = new DirectMessageSettings(true, "jsonl", "unused.db", "direct-search-test.jsonl", 0, 0);
            group = new GroupChatSettings(false, "unused-group.db", 0, 0, 0, 0, 0, 64, 500, true, true);
            identities.put("user-a", new PlayerIdentity("user-a", "Alice", "Alice Display"));
            identities.put("user-b", new PlayerIdentity("user-b", "Bob", "Bob Display"));
            identities.put("user-c", new PlayerIdentity("user-c", "Carol", "Carol Display"));
        }

        @Override public Path dataDirectory() { return dir; }
        @Override public PlayerIdentity resolveIdentity(String uuid) { return identities.get(uuid); }
        @Override public boolean isOnline(String uuid) { return true; }
        @Override public DirectMessageSettings directMessageSettings() { return dm; }
        @Override public GroupChatSettings groupChatSettings() { return group; }
        @Override public void info(String message) { }
        @Override public void warn(String message) { throw new AssertionError("unexpected warning: " + message); }
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("kwc-private-search-");
        DirectMessageStore store = new DirectMessageStore(new Host(temp));
        try {
            store.open();
            DirectMessageStore.SendResult first = store.send("user-a", "user-b", "alpha banana first");
            Thread.sleep(3L);
            long middleStart = System.currentTimeMillis();
            DirectMessageStore.SendResult second = store.send("user-b", "user-a", "project kiwi second");
            Thread.sleep(3L);
            DirectMessageStore.SendResult otherThread = store.send("user-a", "user-c", "banana should never leak");
            check(first.ok && second.ok && otherThread.ok, "fixture messages should send");
            String threadId = first.message.threadId;
            check(threadId.equals(second.message.threadId), "A/B messages share one thread");
            check(!threadId.equals(otherThread.message.threadId), "A/C uses another thread");

            long beforeRead = store.readPosition(threadId, "user-b");
            List<DirectMessageMessage> banana = store.searchMessages("user-b", threadId, "banana", Long.MIN_VALUE, Long.MAX_VALUE, "", 50);
            check(banana.size() == 1, "query searches only the requested DM thread");
            check(banana.get(0).id == first.message.id, "banana result is the A/B message");
            check(store.readPosition(threadId, "user-b") == beforeRead, "search must not change DM read position");

            List<DirectMessageMessage> sender = store.searchMessages("user-a", threadId, "", Long.MIN_VALUE, Long.MAX_VALUE, "bob display", 50);
            check(sender.size() == 1 && sender.get(0).id == second.message.id, "sender filter matches display name");

            List<DirectMessageMessage> querySender = store.searchMessages("user-a", threadId, "alice", Long.MIN_VALUE, Long.MAX_VALUE, "", 50);
            check(querySender.size() == 1 && querySender.get(0).id == first.message.id, "main query can match sender identity");

            List<DirectMessageMessage> date = store.searchMessages("user-a", threadId, "", middleStart, Long.MAX_VALUE, "", 50);
            check(date.size() == 1 && date.get(0).id == second.message.id, "date range filters retained history");

            List<DirectMessageMessage> newestFirst = store.searchMessages("user-a", threadId, "", Long.MIN_VALUE, Long.MAX_VALUE, "", 50);
            check(newestFirst.size() == 2, "filter-only search can return the complete visible thread");
            check(newestFirst.get(0).id == second.message.id && newestFirst.get(1).id == first.message.id, "search results are newest first");

            check(java.util.Arrays.stream(DirectMessageStore.class.getDeclaredMethods())
                    .noneMatch(m -> "hideMessage".equals(m.getName())),
                    "DM message-hide write API is removed");
            check(store.searchMessages("user-b", threadId, "banana", Long.MIN_VALUE, Long.MAX_VALUE, "", 50).size() == 1,
                    "another sender's DM remains visible/searchable because recipient cannot hide it");

            check(store.searchMessages("user-c", threadId, "banana", Long.MIN_VALUE, Long.MAX_VALUE, "", 50).isEmpty(), "non-participant cannot search another DM thread");
            System.out.println("PRIVATE_SEARCH_HARNESS_PASS assertions=" + checks);
        } finally {
            store.close();
            try (var walk = Files.walk(temp)) {
                walk.sorted((a,b) -> b.compareTo(a)).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
            }
        }
    }
}
