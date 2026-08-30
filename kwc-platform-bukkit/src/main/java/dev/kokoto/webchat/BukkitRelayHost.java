package dev.kokoto.webchat;

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
                    peers.add(peer == null ? null : new RelaySettings.Peer(peer.id, peer.url, peer.enabled));
                }
                groups.add(new RelaySettings.Group(group.id, group.sharedSecret, group.forwardingEnabled, peers));
            }
        }
        return new RelaySettings(
                c.serverRelayEnabled, c.serverRelayServerId, c.serverRelayServerName,
                c.serverRelayConnectTimeoutSeconds, c.serverRelayRequestTimeoutSeconds, c.serverRelayMaxClockSkewSeconds,
                c.serverRelayDedupeSeconds, c.serverRelayMaxHops,
                c.serverRelayGameChat, c.serverRelayWebChat, c.serverRelayGuestChat,
                c.serverRelayDiscordChat, c.serverRelaySystemEvents,
                c.serverRelayDeliverToWeb, c.serverRelayDeliverToGame, c.serverRelayGameFormat, groups);
    }

    @Override public String defaultServerName() { return plugin.getServer().getName(); }
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
    public void publishDirectMessageUpdate(String localUserUuid, String remoteUserUuid, String threadId) {
        WebChatServer web = plugin.webServer();
        if (web != null) web.publishDirectMessageUpdate(localUserUuid, remoteUserUuid, threadId);
    }
}
