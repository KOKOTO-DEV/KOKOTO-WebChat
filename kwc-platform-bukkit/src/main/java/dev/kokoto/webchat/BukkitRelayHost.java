package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * BukkitRelayHost는 서버간 Relay Protocol 2.x의 설정·호스트 계약·전송 데이터를 담당한다.
 * BukkitRelayHost participates in configuration, host contracts, or transport data for Relay Protocol 2.x.
 *
 * Relay payload는 서버 경계를 넘으므로 origin/target/sender 식별과 capability negotiation을 신뢰 경계 안에서 다시 검증해야 한다.
 * Relay payloads cross a server trust boundary, so origin/target/sender identity and capability negotiation must be revalidated inside the trust boundary.
 */
import java.util.ArrayList;
import java.util.List;

/** Bukkit-side callbacks used by the platform-neutral core relay engine. */
public final class BukkitRelayHost implements RelayHost {
    private final KokotoWebChatPlugin plugin;

    public BukkitRelayHost(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public RelaySettings relaySettings() {
        ConfigValues c = plugin.configValues();
        if (c == null) return null;
        List<RelaySettings.Group> groups = new ArrayList<>();
        if (c.serverRelayGroups != null) {
            for (ConfigValues.RelayGroup group : c.serverRelayGroups) {
                if (group == null) { groups.add(null); continue; }
                List<RelaySettings.Peer> peers = new ArrayList<>();
                if (group.peers != null) for (ConfigValues.RelayPeer peer : group.peers) {
                    peers.add(peer == null ? null : new RelaySettings.Peer(peer.id, peer.url, peer.enabled,
                            relayPolicy(peer.send), relayPolicy(peer.receive)));
                }
                groups.add(new RelaySettings.Group(group.id, group.sharedSecret, group.forwardingEnabled, peers));
            }
        }
        return new RelaySettings(
                c.serverRelayEnabled, c.serverRelayServerId, c.serverRelayServerName,
                c.serverRelayConnectTimeoutSeconds, c.serverRelayRequestTimeoutSeconds, c.serverRelayMaxClockSkewSeconds,
                c.serverRelayDedupeSeconds, c.serverRelayMaxHops,
                c.serverRelayGameChat, c.serverRelayWebChat, c.serverRelayGuestChat,
                c.serverRelayDiscordChat, c.serverRelaySystemEvents, c.serverRelayEventAnnouncements,
                c.serverRelayDeliverToWeb, c.serverRelayDeliverToGame, c.serverRelayGameFormat, groups);
    }

    private static RelaySettings.DirectionPolicy relayPolicy(ConfigValues.RelayDirectionPolicy policy) {
        if (policy == null) return RelaySettings.DirectionPolicy.allowAll();
        return new RelaySettings.DirectionPolicy(policy.enabled, policy.publicChat, policy.event, policy.dm, policy.profile);
    }

    @Override public String defaultServerName() { return plugin.getServer().getName(); }
    @Override public String productVersion() { return plugin.getDescription().getVersion(); }
    @Override public WebChatLanguage language() { return plugin.langManager(); }
    @Override public void info(String message) { plugin.getLogger().info(message); }
    @Override public void warn(String message) { plugin.getLogger().warning(message); }

    @Override
    public boolean hasPublicMessage(String relayId) {
        WebChatServer web = plugin.webServer();
        return web != null && web.hasMessageId(relayId);
    }

    @Override
    public boolean acceptPublicMessage(ChatMessage message) {
        WebChatServer web = plugin.webServer();
        if (web == null || web.hasMessageId(message == null ? "" : message.id)) return false;
        web.acceptRelayedMessage(message);
        return true;
    }

    @Override
    public boolean acceptPublicReaction(RelayPublicReaction reaction) {
        WebChatServer web = plugin.webServer();
        return reaction != null && web != null && web.acceptRelayedReaction(reaction);
    }

    @Override
    public boolean acceptPublicTyping(RelayPublicTyping typing) {
        WebChatServer web = plugin.webServer();
        return typing != null && web != null && web.acceptRelayedPublicTyping(typing);
    }

    @Override
    public boolean acceptDirectTyping(RelayDirectTyping typing) {
        WebChatServer web = plugin.webServer();
        return typing != null && web != null && web.acceptRelayedDirectTyping(typing);
    }

    @Override
    public boolean hasDirectRelayId(String relayId) {
        DirectMessageStore store = plugin.directMessages();
        return store != null && store.hasRelayId(relayId);
    }

    @Override
    public boolean acceptDirectMessage(RelayDirectMessage message) {
        if (message == null) return false;
        WebChatServer web = plugin.webServer();
        return web != null && web.acceptRelayedDirectMessage(
                message.relayId, message.originServerId, message.originServerName,
                message.senderUuid, message.senderUsername, message.senderDisplayName,
                message.targetUuid, message.targetUsername, message.targetDisplayName,
                message.message, message.gameMessage,
                message.replyToRelayId, message.replyToSender, message.replyToPreview);
    }

    @Override
    public RelayReadApplyResult applyDirectMessageRead(String messageRelayId) {
        DirectMessageStore store = plugin.directMessages();
        DirectMessageStore.ReadReceiptApplyResult applied = store == null ? null : store.applyRemoteReadReceipt(messageRelayId);
        if (applied == null) return RelayReadApplyResult.failed("message_not_found");
        return new RelayReadApplyResult(applied.ok, applied.changed, applied.error,
                applied.localUserUuid, applied.remoteUserUuid, applied.threadId);
    }

    @Override
    public boolean applyDirectMessageDelete(String originServerId, String senderUuid, String messageRelayId) {
        DirectMessageStore store = plugin.directMessages();
        DirectMessageStore.DeleteApplyResult applied = store == null ? null : store.applyRelayedDelete(originServerId, senderUuid, messageRelayId);
        if (applied == null || !applied.ok) return false;
        WebChatServer web = plugin.webServer();
        if (applied.changed && web != null) web.publishDirectMessageUpdate(applied.localUserUuid, applied.remoteUserUuid, applied.threadId);
        return true;
    }

    @Override
    public void publishDirectMessageUpdate(String localUserUuid, String remoteUserUuid, String threadId) {
        WebChatServer web = plugin.webServer();
        if (web != null) web.publishDirectMessageUpdate(localUserUuid, remoteUserUuid, threadId);
    }
    @Override public String handleChatGameRelayRequest(String originServerId, String payloadJson) {
        WebChatServer web = plugin.webServer();
        return web == null ? "{\"ok\":false,\"error\":\"game_relay_unavailable\"}" : web.handleRelayedChatGameRequest(originServerId, payloadJson);
    }
    @Override public String handleProfileRelayRequest(String originServerId, String payloadJson) {
        WebChatServer web = plugin.webServer();
        return web == null ? "{\"ok\":false,\"error\":\"profile_relay_unavailable\"}" : web.handleRelayedProfileRequest(originServerId, payloadJson);
    }

}
