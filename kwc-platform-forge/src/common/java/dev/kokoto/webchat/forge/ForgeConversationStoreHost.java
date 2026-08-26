package dev.kokoto.webchat.forge;

import dev.kokoto.webchat.*;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public final class ForgeConversationStoreHost implements ConversationStoreHost {
    private final KwcForgeRuntime runtime;
    public ForgeConversationStoreHost(KwcForgeRuntime runtime) { this.runtime = runtime; }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }

    @Override
    public PlayerIdentity resolveIdentity(String uuid) {
        String normalized = String.valueOf(uuid == null ? "" : uuid).trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return new PlayerIdentity("", "", "");
        try {
            ServerPlayer online = runtime.server().getPlayerList().getPlayer(UUID.fromString(normalized));
            if (online != null) {
                String username = ForgeCompat.profileName(online);
                String display = runtime.displayPlayerName(online);
                runtime.storage().updateLastDisplayName(normalized, username, display);
                return new PlayerIdentity(normalized, username, display);
            }
        } catch (Exception ignored) {}
        PlayerIdentity known = runtime.storage() == null ? null : runtime.storage().findKnownPlayerByUuid(normalized);
        if (known != null) return known;
        return null;
    }

    @Override public boolean isOnline(String uuid) {
        try { return runtime.server().getPlayerList().getPlayer(UUID.fromString(String.valueOf(uuid))) != null; }
        catch (Exception ignored) { return false; }
    }

    @Override public DirectMessageSettings directMessageSettings() {
        ConfigValues c = runtime.configValues();
        if (c == null) return new DirectMessageSettings(false, "sqlite", "direct-messages.db", "direct-messages.jsonl", 0, 0);
        return new DirectMessageSettings(c.directMessageEnabled, c.directMessageStorage, c.directMessageSqliteFile,
                c.directMessageJsonlFile, c.directMessageRetentionDays, c.directMessageMaxMessagesPerThread);
    }

    @Override public GroupChatSettings groupChatSettings() {
        ConfigValues c = runtime.configValues();
        if (c == null) return new GroupChatSettings(false, "group-messages.db", 0, 0, 0, 0, 72, 32, 500, true, true);
        return new GroupChatSettings(c.groupChatEnabled, c.groupChatSqliteFile, c.groupChatRetentionDays,
                c.groupChatMaxMessagesPerRoom, c.groupChatMaxRoomsPerUser, c.groupChatMaxMembersPerRoom,
                c.groupChatInviteExpireHours, c.groupChatMaxRoomNameLength, c.groupChatMaxMessageLength,
                c.groupChatAllowPublicRooms, c.groupChatAllowRoomPasswords);
    }

    @Override public void info(String message) { runtime.info(message); }
    @Override public void warn(String message) { runtime.warn(message); }
}
