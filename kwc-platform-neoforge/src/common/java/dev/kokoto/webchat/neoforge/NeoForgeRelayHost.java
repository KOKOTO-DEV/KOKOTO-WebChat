package dev.kokoto.webchat.neoforge;


/* KWC 파일 안내 / KWC file guide
 * NeoForgeRelayHost는 서버간 Relay Protocol 2.x의 설정·호스트 계약·전송 데이터를 담당한다.
 * NeoForgeRelayHost participates in configuration, host contracts, or transport data for Relay Protocol 2.x.
 *
 * Relay payload는 서버 경계를 넘으므로 origin/target/sender 식별과 capability negotiation을 신뢰 경계 안에서 다시 검증해야 한다.
 * Relay payloads cross a server trust boundary, so origin/target/sender identity and capability negotiation must be revalidated inside the trust boundary.
 */
import dev.kokoto.webchat.*;
import java.util.ArrayList;
import java.util.List;

public final class NeoForgeRelayHost implements RelayHost {
    private final KwcNeoForgeRuntime runtime;
    public NeoForgeRelayHost(KwcNeoForgeRuntime runtime) { this.runtime = runtime; }
    @Override
    public RelaySettings relaySettings() {
        ConfigValues c = runtime.configValues();
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

    @Override public String defaultServerName() { return runtime.serverName(); }
    @Override public String productVersion() { return runtime.version(); }
    @Override public WebChatLanguage language() { return runtime.langManager(); }
    @Override public void info(String message) { runtime.info(message); }
    @Override public void warn(String message) { runtime.warn(message); }
    @Override public boolean hasPublicMessage(String relayId) { return runtime.webServer() != null && runtime.webServer().hasMessageId(relayId); }
    @Override public boolean acceptPublicMessage(ChatMessage message) {
        if (runtime.webServer() == null || runtime.webServer().hasMessageId(message == null ? "" : message.id)) return false;
        runtime.webServer().acceptRelayedMessage(message); return true;
    }
    @Override public boolean acceptPublicReaction(RelayPublicReaction reaction) {
        return reaction != null && runtime.webServer() != null && runtime.webServer().acceptRelayedReaction(reaction);
    }
    @Override public boolean acceptPublicTyping(RelayPublicTyping typing) {
        return typing != null && runtime.webServer() != null && runtime.webServer().acceptRelayedPublicTyping(typing);
    }
    @Override public boolean acceptDirectTyping(RelayDirectTyping typing) {
        return typing != null && runtime.webServer() != null && runtime.webServer().acceptRelayedDirectTyping(typing);
    }
    @Override public boolean hasDirectRelayId(String relayId) { return runtime.directMessages() != null && runtime.directMessages().hasRelayId(relayId); }
    @Override public boolean acceptDirectMessage(RelayDirectMessage message) {
        return message != null && runtime.webServer() != null && runtime.webServer().acceptRelayedDirectMessage(
                message.relayId, message.originServerId, message.originServerName, message.senderUuid,
                message.senderUsername, message.senderDisplayName, message.targetUuid, message.targetUsername,
                message.targetDisplayName, message.message, message.gameMessage,
                message.replyToRelayId, message.replyToSender, message.replyToPreview);
    }
    @Override public RelayReadApplyResult applyDirectMessageRead(String messageRelayId) {
        DirectMessageStore.ReadReceiptApplyResult applied = runtime.directMessages() == null ? null : runtime.directMessages().applyRemoteReadReceipt(messageRelayId);
        if (applied == null) return RelayReadApplyResult.failed("message_not_found");
        return new RelayReadApplyResult(applied.ok, applied.changed, applied.error, applied.localUserUuid, applied.remoteUserUuid, applied.threadId);
    }
    @Override public boolean applyDirectMessageDelete(String originServerId, String senderUuid, String messageRelayId) {
        DirectMessageStore.DeleteApplyResult applied = runtime.directMessages() == null ? null : runtime.directMessages().applyRelayedDelete(originServerId, senderUuid, messageRelayId);
        if (applied == null || !applied.ok) return false;
        if (applied.changed && runtime.webServer() != null) runtime.webServer().publishDirectMessageUpdate(applied.localUserUuid, applied.remoteUserUuid, applied.threadId);
        return true;
    }
    @Override public void publishDirectMessageUpdate(String localUserUuid, String remoteUserUuid, String threadId) {
        if (runtime.webServer() != null) runtime.webServer().publishDirectMessageUpdate(localUserUuid, remoteUserUuid, threadId);
    }
    @Override public String handleChatGameRelayRequest(String originServerId, String payloadJson) {
        WebChatServer web = runtime.webServer();
        return web == null ? "{\"ok\":false,\"error\":\"game_relay_unavailable\"}" : web.handleRelayedChatGameRequest(originServerId, payloadJson);
    }
    @Override public String handleProfileRelayRequest(String originServerId, String payloadJson) {
        WebChatServer web = runtime.webServer();
        return web == null ? "{\"ok\":false,\"error\":\"profile_relay_unavailable\"}" : web.handleRelayedProfileRequest(originServerId, payloadJson);
    }

}
