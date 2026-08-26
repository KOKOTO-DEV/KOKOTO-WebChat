package dev.kokoto.webchat.forge;

import dev.kokoto.webchat.*;
import java.util.ArrayList;
import java.util.List;

public final class ForgeRelayHost implements RelayHost {
    private final KwcForgeRuntime runtime;
    public ForgeRelayHost(KwcForgeRuntime runtime) { this.runtime = runtime; }
    @Override public RelaySettings relaySettings() {
        ConfigValues c = runtime.configValues();
        if (c == null) return null;
        List<RelaySettings.Peer> peers = new ArrayList<>();
        if (c.serverRelayPeers != null) for (ConfigValues.RelayPeer peer : c.serverRelayPeers) {
            peers.add(peer == null ? null : new RelaySettings.Peer(peer.id, peer.url, peer.secret, peer.enabled));
        }
        return new RelaySettings(c.serverRelayEnabled, c.serverRelayServerId, c.serverRelayServerName, c.serverRelaySharedSecret,
                c.serverRelayConnectTimeoutSeconds, c.serverRelayRequestTimeoutSeconds, c.serverRelayMaxClockSkewSeconds,
                c.serverRelayDedupeSeconds, c.serverRelayMaxHops, c.serverRelayForwardReceivedPublicChat, c.serverRelayGameChat, c.serverRelayWebChat,
                c.serverRelayGuestChat, c.serverRelayDiscordChat, c.serverRelaySystemEvents,
                c.serverRelayDeliverToWeb, c.serverRelayDeliverToGame, c.serverRelayGameFormat, peers);
    }
    @Override public String defaultServerName() { return runtime.serverName(); }
    @Override public void info(String message) { runtime.info(message); }
    @Override public void warn(String message) { runtime.warn(message); }
    @Override public boolean hasPublicMessage(String relayId) { return runtime.webServer() != null && runtime.webServer().hasMessageId(relayId); }
    @Override public boolean acceptPublicMessage(ChatMessage message) {
        if (runtime.webServer() == null || runtime.webServer().hasMessageId(message == null ? "" : message.id)) return false;
        runtime.webServer().acceptRelayedMessage(message); return true;
    }
    @Override public boolean hasDirectRelayId(String relayId) { return runtime.directMessages() != null && runtime.directMessages().hasRelayId(relayId); }
    @Override public boolean acceptDirectMessage(RelayDirectMessage message) {
        return message != null && runtime.webServer() != null && runtime.webServer().acceptRelayedDirectMessage(
                message.relayId, message.originServerId, message.originServerName, message.senderUuid,
                message.senderUsername, message.senderDisplayName, message.targetUuid, message.targetUsername,
                message.targetDisplayName, message.message, message.gameMessage);
    }
    @Override public RelayReadApplyResult applyDirectMessageRead(String messageRelayId) {
        DirectMessageStore.ReadReceiptApplyResult applied = runtime.directMessages() == null ? null : runtime.directMessages().applyRemoteReadReceipt(messageRelayId);
        if (applied == null) return RelayReadApplyResult.failed("message_not_found");
        return new RelayReadApplyResult(applied.ok, applied.changed, applied.error, applied.localUserUuid, applied.remoteUserUuid, applied.threadId);
    }
    @Override public void publishDirectMessageUpdate(String localUserUuid, String remoteUserUuid, String threadId) {
        if (runtime.webServer() != null) runtime.webServer().publishDirectMessageUpdate(localUserUuid, remoteUserUuid, threadId);
    }
}
