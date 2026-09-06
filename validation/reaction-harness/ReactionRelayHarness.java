import com.sun.net.httpserver.HttpServer;
import dev.kokoto.webchat.*;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ReactionRelayHarness {
    static int assertions = 0;
    static void check(boolean v, String m) { assertions++; if (!v) throw new AssertionError(m); }

    static final class Host implements RelayHost {
        final String id, name;
        final RelaySettings settings;
        final List<RelayPublicReaction> reactions = new CopyOnWriteArrayList<>();
        final List<RelayDirectTyping> typings = new CopyOnWriteArrayList<>();
        final List<RelayPublicTyping> publicTypings = new CopyOnWriteArrayList<>();
        Host(String id, int selfPort, Map<String,Integer> peers) {
            this.id=id; this.name=id.toUpperCase(Locale.ROOT);
            List<RelaySettings.Peer> ps = new ArrayList<>();
            for (var e: peers.entrySet()) ps.add(new RelaySettings.Peer(e.getKey(), "http://127.0.0.1:"+e.getValue()+"/api", true));
            var g = new RelaySettings.Group("g1", "0123456789abcdef0123456789abcdef0123456789abcdef", true, ps);
            settings = new RelaySettings(true,id,name,2,3,60,120,4,true,true,true,true,true,true,true,"",List.of(g));
        }
        public RelaySettings relaySettings(){return settings;}
        public String defaultServerName(){return name;}
        public WebChatLanguage language(){return null;}
        public void info(String m){}
        public void warn(String m){}
        public boolean hasPublicMessage(String id){return true;}
        public boolean acceptPublicMessage(ChatMessage m){return true;}
        public boolean acceptPublicReaction(RelayPublicReaction r){reactions.add(r); return true;}
        public boolean acceptPublicTyping(RelayPublicTyping t){publicTypings.add(t); return true;}
        public boolean acceptDirectTyping(RelayDirectTyping t){typings.add(t); return true;}
        public boolean hasDirectRelayId(String id){return false;}
        public boolean acceptDirectMessage(RelayDirectMessage m){return false;}
        public RelayReadApplyResult applyDirectMessageRead(String id){return RelayReadApplyResult.failed("not_found");}
        public void publishDirectMessageUpdate(String a,String b,String c){}
    }

    static int freePort() throws Exception {
        try (var s = new java.net.ServerSocket(0)) { return s.getLocalPort(); }
    }
    static HttpServer serve(int port, ServerRelay relay) throws Exception {
        HttpServer h=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        h.createContext("/api/relay/v2/message", relay::handleMessage);
        h.start(); return h;
    }
    static void waitFor(java.util.function.BooleanSupplier ok) throws Exception {
        long until=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<until){ if(ok.getAsBoolean()) return; Thread.sleep(25); }
        throw new AssertionError("timeout");
    }

    public static void main(String[] args) throws Exception {
        int pa=freePort(), pb=freePort(), pc=freePort();
        Host ha=new Host("a",pa,Map.of("b",pb,"c",pc));
        Host hb=new Host("b",pb,Map.of("a",pa));
        Host hc=new Host("c",pc,Map.of("a",pa));
        try (ServerRelay ra=new ServerRelay(ha); ServerRelay rb=new ServerRelay(hb); ServerRelay rc=new ServerRelay(hc)) {
            HttpServer sa=serve(pa,ra), sb=serve(pb,rb), sc=serve(pc,rc);
            try {
                check(ra.isEnabled() && rb.isEnabled() && rc.isEnabled(), "relays active");
                ra.publishPublicReaction("relay-message-123456", "11111111-1111-1111-1111-111111111111", "Alice Display", "👍", true);
                waitFor(() -> hb.reactions.size()==1 && hc.reactions.size()==1);
                check(hb.reactions.get(0).reaction.equals("👍"), "unicode B");
                check(hc.reactions.get(0).active, "unicode C active");
                check(hb.reactions.get(0).actorLabel.equals("Alice Display"), "reaction actor label B");
                check(hc.reactions.get(0).actorLabel.equals("Alice Display"), "reaction actor label C");
                ra.publishPublicReaction("relay-message-123456", "11111111-1111-1111-1111-111111111111", ":default/wave:", true);
                waitFor(() -> hb.reactions.size()==2 && hc.reactions.size()==2);
                check(hb.reactions.get(1).reaction.equals(":default/wave:"), "custom canonical B");
                check(hc.reactions.get(1).reaction.equals(":default/wave:"), "custom canonical C");
                ra.publishPublicReaction("relay-message-123456", "11111111-1111-1111-1111-111111111111", ":default/wave:", false);
                waitFor(() -> hb.reactions.size()==3 && hc.reactions.size()==3);
                check(!hb.reactions.get(2).active && !hc.reactions.get(2).active, "remove fanout");
                check(hb.reactions.stream().allMatch(r -> r.messageRelayId.equals("relay-message-123456")), "stable relay target B");
                check(hc.reactions.stream().allMatch(r -> r.messageRelayId.equals("relay-message-123456")), "stable relay target C");
                long expiry = System.currentTimeMillis() + 5000L;
                check(ra.publishDirectTyping("b", "11111111-1111-1111-1111-111111111111", "Alice", "Alice Display",
                        "22222222-2222-2222-2222-222222222222", expiry).get(), "typing delivery result");
                waitFor(() -> hb.typings.size()==1);
                check(hb.typings.get(0).targetUuid.equals("22222222-2222-2222-2222-222222222222"), "typing target B");
                check(hb.typings.get(0).senderUsername.equals("Alice"), "typing sender B");
                check(hb.typings.get(0).expiresAt == expiry, "typing expiry B");
                check(hc.typings.isEmpty(), "typing not leaked to C");

                long publicExpiry = System.currentTimeMillis() + 5000L;
                ra.publishPublicTyping("web", "11111111-1111-1111-1111-111111111111", "Alice", "<gold>Alice Display</gold>",
                        "pt-browser-client-12345", publicExpiry);
                waitFor(() -> hb.publicTypings.size()==1 && hc.publicTypings.size()==1);
                check(hb.publicTypings.get(0).senderUsername.equals("Alice"), "public typing sender B");
                check(hc.publicTypings.get(0).senderDisplayName.equals("<gold>Alice Display</gold>"), "public typing display C");
                check(hb.publicTypings.get(0).clientId.equals("pt-browser-client-12345"), "public typing client id B");
                check(hc.publicTypings.get(0).expiresAt == publicExpiry, "public typing expiry C");

                // 5.2 reaction authority: a remote server sends a mutation request to
                // the message-origin server. Only the origin accepts the request; its
                // committed event then returns to mirrors with the same stable event ID.
                ha.reactions.clear(); hb.reactions.clear(); hc.reactions.clear();
                String authorityEvent = "react-authority-123456";
                ServerRelay.ReactionRequestResult authority = ra.requestPublicReaction(authorityEvent, "c",
                        "relay-owner-message-123456", "11111111-1111-1111-1111-111111111111",
                        "Alice Display", "❤️", true).get();
                check(authority.committed && !authority.retryable, "authority request committed");
                waitFor(() -> hc.reactions.size()==1 && ha.reactions.size()==1);
                check(hc.reactions.get(0).request, "origin receives request envelope");
                check(hc.reactions.get(0).targetServerId.equals("c"), "request targets message origin");
                check(hc.reactions.get(0).eventId.equals(authorityEvent), "request stable event id");
                check(!ha.reactions.get(0).request, "requester receives committed envelope");
                check(ha.reactions.get(0).originServerId.equals("c"), "commit authority is message origin");
                check(ha.reactions.get(0).eventId.equals(authorityEvent), "commit reuses request event id");
                check(ha.reactions.get(0).reaction.equals("❤️") && ha.reactions.get(0).active, "commit carries final state");
                check(hb.reactions.isEmpty(), "authority commit not leaked outside origin route group");
                ServerRelay.ReactionRequestResult unavailable = ra.requestPublicReaction("react-unavailable-123", "missing",
                        "relay-owner-message-123456", "11111111-1111-1111-1111-111111111111",
                        "Alice Display", "👍", true).get();
                check(!unavailable.committed && unavailable.retryable, "unavailable authority is retryable");

                // Private-message reactions use the same authenticated routing but do
                // not publish a committed fan-out event. Only the targeted peer sees
                // the mutation request, preventing DM metadata from leaking to C.
                ha.reactions.clear(); hb.reactions.clear(); hc.reactions.clear();
                String dmEvent = "react-dm-private-123456";
                ServerRelay.ReactionRequestResult dmResult = ra.requestReaction("dm", dmEvent, "b",
                        "dm-relay-message-123456", "11111111-1111-1111-1111-111111111111",
                        "Alice Display", "👍", true).get();
                check(dmResult.committed && !dmResult.retryable, "dm reaction request committed");
                waitFor(() -> hb.reactions.size()==1);
                check(hb.reactions.get(0).request, "dm target receives request envelope");
                check(hb.reactions.get(0).scope.equals("dm"), "dm request preserves private scope");
                check(hb.reactions.get(0).eventId.equals(dmEvent), "dm request stable event id");
                check(ha.reactions.isEmpty(), "dm requester receives no public commit fanout");
                check(hc.reactions.isEmpty(), "dm reaction not leaked to unrelated peer");
            } finally { sa.stop(0); sb.stop(0); sc.stop(0); }
        }

        java.nio.file.Path temp=java.nio.file.Files.createTempDirectory("kwc-reactions-");
        CoreLogger log=new CoreLogger(){ public void info(String m){} public void warn(String m){} };
        PublicReactionStore store=new PublicReactionStore(temp,log);
        check(store.apply("m1","u1","§x§b§7§a§e§b§c§lAlice","👍",true),"store add");
        check(store.apply("m1","u2","Bob","👍",true),"store second actor");
        check(store.summary("m1","u1").get(0).count==2,"store count");
        check(store.summary("m1","u1").get(0).mine,"store mine");
        check(store.summary("m1","u1").get(0).actors.size()==2,"store actor projection count");
        check(store.summary("m1","u1").get(0).actors.get(0).label.equals("Alice"),"store strips actor formatting");
        check(store.apply("m1","u1","👍",false),"store remove");
        PublicReactionStore reload=new PublicReactionStore(temp,log);
        check(reload.summary("m1","u1").get(0).count==1,"persistence replay");
        check(!reload.summary("m1","u1").get(0).mine,"persistence mine false");
        check(reload.summary("m1","u1").get(0).actors.get(0).label.equals("Bob"),"actor label persistence");

        java.nio.file.Path catalogTemp=java.nio.file.Files.createTempDirectory("kwc-reaction-catalog-");
        ReactionCatalogStore catalog=new ReactionCatalogStore(catalogTemp,log);
        check(catalog.allows("👍"),"catalog default unicode allowed");
        check(catalog.allows(":default/wave:"),"catalog default custom allowed");
        check(catalog.snapshot().showActorList,"catalog actor list default enabled");
        String defaultCatalogJson=catalog.snapshot().toJson();
        check(defaultCatalogJson.contains("\"searchNames\""),"catalog exposes unicode search metadata");
        check(defaultCatalogJson.contains("thumbs up sign"),"catalog exposes default unicode character name");
        check("thumbs up like yes good 좋아요 엄지 찬성 굿 いいね 賛成 点赞 赞 同意".equals(catalog.snapshot().searchAliases.get("👍")),"catalog exposes editable default search aliases");
        Map<String,String> managed=new LinkedHashMap<>();
        for (String id : ReactionCatalogStore.CATEGORY_IDS) managed.put(id, "");
        managed.put("smileys", "😂 🦖");
        managed.put("enabled", "true");
        managed.put("customEmojiEnabled", "false");
        managed.put("showActorList", "false");
        managed.put("searchAliases", "😂 = laugh tears 웃음\n🦖 = dinosaur 공룡 恐竜 恐龙");
        catalog.save(managed);
        check(catalog.enabled(),"catalog master enabled");
        check(catalog.allows("😂"),"catalog managed unicode allowed");
        check(catalog.snapshot().toJson().contains("t-rex"),"catalog exposes admin-added unicode character name");
        check(catalog.snapshot().toJson().contains("dinosaur 공룡"),"catalog exposes admin-managed search alias");
        check(!catalog.allows("👍"),"catalog disabled unicode blocked");
        check(!catalog.allows(":default/wave:"),"catalog custom disabled");
        ReactionCatalogStore catalogReload=new ReactionCatalogStore(catalogTemp,log);
        check(catalogReload.allows("😂") && !catalogReload.allows("👍"),"catalog persistence");
        check(!catalogReload.snapshot().customEmojiEnabled,"catalog custom setting persistence");
        check(!catalogReload.snapshot().showActorList,"catalog actor list setting persistence");
        check("dinosaur 공룡 恐竜 恐龙".equals(catalogReload.snapshot().searchAliases.get("🦖")),"catalog alias file persistence");
        Map<String,String> disabled=new LinkedHashMap<>();
        for (String id : ReactionCatalogStore.CATEGORY_IDS) disabled.put(id, String.join(" ", catalogReload.snapshot().categories.get(id)));
        disabled.put("enabled", "false");
        disabled.put("customEmojiEnabled", "false");
        disabled.put("showActorList", "false");
        catalogReload.save(disabled);
        check(!catalogReload.enabled(),"catalog master disable persistence source");
        check(!catalogReload.allows("😂"),"catalog master disable blocks new unicode");
        ReactionCatalogStore disabledReload=new ReactionCatalogStore(catalogTemp,log);
        check(!disabledReload.enabled(),"catalog master disable reload");
        disabledReload.resetDefaults();
        check(disabledReload.enabled() && disabledReload.allows("👍") && disabledReload.snapshot().customEmojiEnabled && disabledReload.snapshot().showActorList,"catalog reset defaults");
        check(disabledReload.snapshot().searchAliases.containsKey("👍"),"catalog reset restores default aliases");
        System.out.println("REACTION_RELAY_HARNESS_PASS assertions="+assertions);
    }
}
