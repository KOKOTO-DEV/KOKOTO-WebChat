package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * DirectMessageDeleteHarness는 validation 모듈의 KWC 구현 파일이다. 클래스 이름이 나타내는 책임을 이 파일 안에 한정해 다른 계층과의 결합을 줄인다.
 * DirectMessageDeleteHarness is a KWC implementation file in the validation module. Keep the responsibility implied by the class name localized here to reduce cross-layer coupling.
 *
 * 변경 시 호출자와 반환값뿐 아니라 인증/권한, thread context, persistence, multi-loader 호환성에 미치는 영향을 함께 확인한다.
 * When changing it, review not only callers/returns but also effects on authorization, thread context, persistence, and multi-loader compatibility.
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DirectMessageDeleteHarness {
    private static int checks;

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
            dm = new DirectMessageSettings(true, "jsonl", "unused.db", "direct-delete-test.jsonl", 0, 0);
            group = new GroupChatSettings(false, "unused-group.db", 0, 0, 0, 0, 0, 64, 500, true, true);
            identities.put("user-a", new PlayerIdentity("user-a", "Alice", "Alice"));
            identities.put("user-b", new PlayerIdentity("user-b", "Bob", "Bob"));
        }

        @Override public Path dataDirectory() { return dir; }
        @Override public PlayerIdentity resolveIdentity(String uuid) { return identities.get(uuid); }
        @Override public boolean isOnline(String uuid) { return true; }
        @Override public DirectMessageSettings directMessageSettings() { return dm; }
        @Override public GroupChatSettings groupChatSettings() { return group; }
        @Override public void info(String message) { }
        @Override public void warn(String message) { throw new AssertionError("unexpected warning: " + message); }
    }

    private static List<DirectMessageMessage> all(DirectMessageStore store, String user, String threadId) {
        return store.searchMessages(user, threadId, "", Long.MIN_VALUE, Long.MAX_VALUE, "", 100);
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("kwc-dm-delete-");
        Host host = new Host(temp);
        DirectMessageStore store = new DirectMessageStore(host);
        try {
            store.open();

            DirectMessageStore.SendResult received = store.send("user-a", "user-b", "recipient cannot hide or delete this message");
            check(received.ok, "local fixture send succeeds");
            DirectMessageStore.DeletePlan recipientPlan = store.deletePlan("user-b", received.message.id);
            check(recipientPlan.ok && !recipientPlan.owner, "recipient is not allowed sender-owned delete");
            check(java.util.Arrays.stream(DirectMessageStore.class.getDeclaredMethods())
                    .noneMatch(m -> "hideMessage".equals(m.getName())),
                    "DM store exposes no message-hide write API");
            check(all(store, "user-b", received.message.threadId).stream().anyMatch(m -> m.id == received.message.id),
                    "recipient still sees another sender's DM");
            check(all(store, "user-a", received.message.threadId).stream().anyMatch(m -> m.id == received.message.id),
                    "sender still sees DM that recipient cannot modify");

            DirectMessageStore.SendResult localOwned = store.send("user-a", "user-b", "delete for both local users");
            check(localOwned.ok, "second local fixture send succeeds");
            DirectMessageStore.SendResult localReply = store.send("user-b", "user-a", "reply survives but quote is scrubbed", localOwned.message.id);
            check(localReply.ok && localReply.message.replyToId == localOwned.message.id, "local reply fixture captures deleted-message snapshot");
            DirectMessageStore.DeletePlan localPlan = store.deletePlan("user-a", localOwned.message.id);
            check(localPlan.ok && localPlan.owner && localPlan.targetServerId.isBlank(), "local sender-owned message has no relay target");
            DirectMessageStore.DeleteApplyResult localDeleted = store.deleteOwnedMessage("user-a", localOwned.message.id);
            check(localDeleted.ok && localDeleted.changed, "sender-owned local DM is globally deleted");
            check(all(store, "user-a", localOwned.message.threadId).stream().noneMatch(m -> m.id == localOwned.message.id), "local sender no longer sees deleted DM");
            check(all(store, "user-b", localOwned.message.threadId).stream().noneMatch(m -> m.id == localOwned.message.id), "local recipient no longer sees deleted DM");
            DirectMessageMessage localReplyAfterDelete = all(store, "user-b", localOwned.message.threadId).stream()
                    .filter(m -> m.id == localReply.message.id).findFirst().orElseThrow();
            check(localReplyAfterDelete.replyToId == 0L && localReplyAfterDelete.replyToSender.isBlank()
                    && localReplyAfterDelete.replyToPreview.isBlank() && localReplyAfterDelete.replyToRelayId.isBlank(),
                    "local delete scrubs reply snapshot of deleted body");

            String remoteB = RemotePlayerRef.key("server2", "user-b");
            DirectMessageStore.SendResult outbound = store.sendPendingRemote("user-a", remoteB, "remote outbound delete", "dmrelay-delete-outbound", "server2", "client-delete-1");
            check(outbound.ok, "remote outbound fixture send succeeds");
            store.updateDeliveryStatus(outbound.message.id, "delivered", "");
            DirectMessageStore.DeletePlan outboundPlan = store.deletePlan("user-a", outbound.message.id);
            check(outboundPlan.ok && outboundPlan.owner, "outbound remote message is sender-owned");
            check("server2".equals(outboundPlan.targetServerId), "outbound delete plan resolves remote target server");
            check("dmrelay-delete-outbound".equals(outboundPlan.relayId), "outbound delete plan preserves relay id");
            DirectMessageStore.DeleteApplyResult outboundDeleted = store.deleteOwnedMessage("user-a", outbound.message.id);
            check(outboundDeleted.ok, "outbound local tombstone applies after relay acknowledgement");
            check(store.hasRelayId("dmrelay-delete-outbound"), "deleted outbound relay id remains a dedupe tombstone");

            String remoteA = RemotePlayerRef.key("server1", "user-a");
            DirectMessageStore.SendResult inbound = store.receiveRelayed(remoteA, "user-b", "remote inbound delete", "dmrelay-delete-inbound");
            check(inbound.ok, "remote inbound fixture receive succeeds");
            DirectMessageStore.SendResult remoteReply = store.sendPendingRemote("user-b", remoteA,
                    "reply to remote message", "dmrelay-reply-outbound", "server1", "client-delete-reply", inbound.message.id);
            check(remoteReply.ok && "dmrelay-delete-inbound".equals(remoteReply.message.replyToRelayId),
                    "remote reply fixture stores original relay id snapshot");
            DirectMessageStore.DeleteApplyResult wrong = store.applyRelayedDelete("server1", "somebody-else", "dmrelay-delete-inbound");
            check(!wrong.ok, "wrong remote sender cannot delete another sender's relayed DM");
            DirectMessageStore.DeleteApplyResult inboundDeleted = store.applyRelayedDelete("server1", "user-a", "dmrelay-delete-inbound");
            check(inboundDeleted.ok && inboundDeleted.changed, "origin sender can delete relayed DM on target server");
            check(all(store, "user-b", inbound.message.threadId).stream().noneMatch(m -> m.id == inbound.message.id),
                    "target user no longer sees remotely deleted DM");
            DirectMessageMessage remoteReplyAfterDelete = all(store, "user-b", inbound.message.threadId).stream()
                    .filter(m -> m.id == remoteReply.message.id).findFirst().orElseThrow();
            check(remoteReplyAfterDelete.replyToId == 0L && remoteReplyAfterDelete.replyToSender.isBlank()
                    && remoteReplyAfterDelete.replyToPreview.isBlank() && remoteReplyAfterDelete.replyToRelayId.isBlank(),
                    "relayed delete scrubs reply snapshot using relay id");
            DirectMessageStore.DeleteApplyResult duplicateDelete = store.applyRelayedDelete("server1", "user-a", "dmrelay-delete-inbound");
            check(duplicateDelete.ok && !duplicateDelete.changed, "repeated relay delete is idempotent");
            check(store.hasRelayId("dmrelay-delete-inbound"), "inbound deleted relay id stays available for dedupe/idempotence");

            store.close();
            store.open();
            check(store.hasRelayId("dmrelay-delete-outbound"), "outbound delete tombstone survives JSONL restart");
            check(store.hasRelayId("dmrelay-delete-inbound"), "inbound delete tombstone survives JSONL restart");
            check(all(store, "user-b", inbound.message.threadId).stream().noneMatch(m -> m.id == inbound.message.id),
                    "remotely deleted DM stays deleted after restart");
            DirectMessageMessage remoteReplyAfterRestart = all(store, "user-b", inbound.message.threadId).stream()
                    .filter(m -> m.id == remoteReply.message.id).findFirst().orElseThrow();
            check(remoteReplyAfterRestart.replyToId == 0L && remoteReplyAfterRestart.replyToPreview.isBlank(),
                    "scrubbed remote reply snapshot stays scrubbed after JSONL restart");
            DirectMessageMessage localReplyAfterRestart = all(store, "user-b", localOwned.message.threadId).stream()
                    .filter(m -> m.id == localReply.message.id).findFirst().orElseThrow();
            check(localReplyAfterRestart.replyToId == 0L && localReplyAfterRestart.replyToPreview.isBlank(),
                    "scrubbed local reply snapshot stays scrubbed after JSONL restart");
            check(all(store, "user-b", received.message.threadId).stream().anyMatch(m -> m.id == received.message.id),
                    "recipient-nonowned DM remains visible after restart because no hide operation exists");

            System.out.println("DIRECT_MESSAGE_DELETE_HARNESS_PASS assertions=" + checks);
        } finally {
            store.close();
            try (var walk = Files.walk(temp)) {
                walk.sorted((a,b) -> b.compareTo(a)).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
            }
        }
    }
}
