/* KWC 파일 안내 / KWC file guide
 * RC32 Relay peer 정책 검증: peer별 송신/수신과 public-chat/event/dm/profile 트래픽 분리가 Protocol 2.2에서 독립적으로 동작하는지 확인한다.
 * RC32 Relay peer-policy regression: verifies independent per-peer send/receive gates and public-chat/event/dm/profile traffic classes while keeping Protocol 2.2.
 */

import com.sun.net.httpserver.HttpServer;
import dev.kokoto.webchat.*;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** RC32 regression: peer send/receive directions and traffic classes stay independent on Relay 2.2. */
public final class RelayPeerPolicyHarness {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }

    private static final RelaySettings.DirectionPolicy ALL = RelaySettings.DirectionPolicy.allowAll();
    private static RelaySettings.DirectionPolicy policy(boolean enabled, boolean publicChat, boolean event, boolean dm, boolean profile) {
        return new RelaySettings.DirectionPolicy(enabled, publicChat, event, dm, profile);
    }

    private static final class Host implements RelayHost {
        final String id;
        final RelaySettings settings;
        final List<ChatMessage> publicMessages = new CopyOnWriteArrayList<>();
        final List<RelayDirectTyping> directTypings = new CopyOnWriteArrayList<>();

        Host(String id, String peerId, int peerPort, RelaySettings.DirectionPolicy send, RelaySettings.DirectionPolicy receive) {
            this.id = id;
            RelaySettings.Peer peer = new RelaySettings.Peer(peerId, "http://127.0.0.1:" + peerPort + "/api", true, send, receive);
            RelaySettings.Group group = new RelaySettings.Group("g1", "0123456789abcdef0123456789abcdef0123456789abcdef", false, List.of(peer));
            settings = new RelaySettings(true, id, id.toUpperCase(Locale.ROOT), 2, 3, 60, 120, 4,
                    true, true, true, true, false, true, true, true, "", List.of(group));
        }
        public RelaySettings relaySettings() { return settings; }
        public String defaultServerName() { return id; }
        public WebChatLanguage language() { return null; }
        public void info(String message) {}
        public void warn(String message) {}
        public boolean hasPublicMessage(String relayId) { return publicMessages.stream().anyMatch(m -> relayId.equals(m.relayId)); }
        public boolean acceptPublicMessage(ChatMessage message) { publicMessages.add(message); return true; }
        public boolean acceptPublicReaction(RelayPublicReaction reaction) { return true; }
        public boolean acceptPublicTyping(RelayPublicTyping typing) { return true; }
        public boolean acceptDirectTyping(RelayDirectTyping typing) { directTypings.add(typing); return true; }
        public boolean hasDirectRelayId(String relayId) { return false; }
        public boolean acceptDirectMessage(RelayDirectMessage message) { return true; }
        public RelayReadApplyResult applyDirectMessageRead(String relayId) { return RelayReadApplyResult.failed("not_found"); }
        public boolean applyDirectMessageDelete(String originServerId, String senderUuid, String relayId) { return true; }
        public void publishDirectMessageUpdate(String localUserUuid, String remoteUserUuid, String threadId) {}
        public String handleChatGameRelayRequest(String originServerId, String payloadJson) { return "{\"ok\":true,\"kind\":\"event\"}"; }
        public String handleProfileRelayRequest(String originServerId, String payloadJson) { return "{\"ok\":true,\"kind\":\"profile\"}"; }
    }

    private static int freePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) { return socket.getLocalPort(); }
    }
    private static HttpServer serve(int port, ServerRelay relay) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/relay/v2/message", relay::handleMessage);
        server.start();
        return server;
    }
    private static void waitFor(java.util.function.BooleanSupplier test) throws Exception {
        long until = System.currentTimeMillis() + 4000L;
        while (System.currentTimeMillis() < until) { if (test.getAsBoolean()) return; Thread.sleep(20L); }
        throw new AssertionError("timeout");
    }

    public static void main(String[] args) throws Exception {
        // Compatibility: old 3-field peer declarations implicitly allow both directions and all four classes.
        RelaySettings.Peer legacy = new RelaySettings.Peer("b", "http://127.0.0.1:1/api", true);
        check(legacy.send.enabled && legacy.receive.enabled, "legacy peer defaults both directions on");
        check(legacy.send.publicChat && legacy.send.event && legacy.send.dm && legacy.send.profile, "legacy send defaults all classes on");
        check(legacy.receive.publicChat && legacy.receive.event && legacy.receive.dm && legacy.receive.profile, "legacy receive defaults all classes on");

        int pa = freePort(), pb = freePort();
        Host a = new Host("a", "b", pb, ALL, ALL);
        Host b = new Host("b", "a", pa, ALL, policy(true, true, false, true, true));
        try (ServerRelay ra = new ServerRelay(a); ServerRelay rb = new ServerRelay(b)) {
            HttpServer sa = serve(pa, ra), sb = serve(pb, rb);
            try {
                check(ra.canRouteChatGame("b"), "sender can route event when local send.event is enabled");
                ServerRelay.ChatGameRelayResponse denied = ra.requestChatGame("b", "{\"action\":\"list\"}").get();
                check(denied.status == 403 && denied.body.contains("peer_receive_disabled"), "receiver independently rejects event traffic");

                ChatMessage publicChat = new ChatMessage(System.currentTimeMillis(), "web", "Alice", "USER", "hello");
                ra.publishLocal(publicChat);
                waitFor(() -> b.publicMessages.size() == 1);
                check("web".equals(b.publicMessages.get(0).source), "receive.public-chat remains independent from receive.event");

                ChatMessage event = new ChatMessage(System.currentTimeMillis(), "event", "Game", "SYSTEM", "event notice");
                ra.publishLocal(event);
                Thread.sleep(300L);
                check(b.publicMessages.size() == 1, "event announcement is blocked without blocking normal public chat");

                long expiry = System.currentTimeMillis() + 5000L;
                check(ra.publishDirectTyping("b", "11111111-1111-1111-1111-111111111111", "Alice", "Alice",
                        "22222222-2222-2222-2222-222222222222", expiry).get(), "receive.dm remains enabled");
                waitFor(() -> b.directTypings.size() == 1);
            } finally { sa.stop(0); sb.stop(0); }
        }

        int pc = freePort(), pd = freePort();
        Host c = new Host("c", "d", pd, policy(true, true, false, true, true), ALL);
        Host d = new Host("d", "c", pc, ALL, ALL);
        try (ServerRelay rc = new ServerRelay(c); ServerRelay rd = new ServerRelay(d)) {
            HttpServer sc = serve(pc, rc), sd = serve(pd, rd);
            try {
                check(!rc.canRouteChatGame("d"), "send.event=false removes the local event route");
                check(rc.canRouteDirectMessage("d"), "send.dm remains routable when send.event is disabled");
                ServerRelay.ChatGameRelayResponse blocked = rc.requestChatGame("d", "{\"action\":\"list\"}").get();
                check(blocked.status == 503, "local send policy fails closed before event transport");
                long expiry = System.currentTimeMillis() + 5000L;
                check(rc.publishDirectTyping("d", "33333333-3333-3333-3333-333333333333", "Carol", "Carol",
                        "44444444-4444-4444-4444-444444444444", expiry).get(), "send.dm still works independently");
                waitFor(() -> d.directTypings.size() == 1);
            } finally { sc.stop(0); sd.stop(0); }
        }

        System.out.println("RC32_RELAY_PEER_POLICY_PASS assertions=" + assertions);
    }
}
