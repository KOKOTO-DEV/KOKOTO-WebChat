package dev.kokoto.webchat;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.UUID;

/** Bukkit identity/config callbacks for the platform-neutral DM and group stores. */
public final class BukkitConversationStoreHost implements ConversationStoreHost {
    private final KokotoWebChatPlugin plugin;

    public BukkitConversationStoreHost(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Path dataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public PlayerIdentity resolveIdentity(String uuid) {
        String normalized = normalizeUuid(uuid);
        if (normalized.isBlank()) return new PlayerIdentity("", "", "");

        try {
            Player online = plugin.getServer().getPlayer(UUID.fromString(normalized));
            if (online != null) {
                String username = online.getName() == null ? "" : online.getName();
                String displayName = plugin.displayPlayerName(online);
                if (displayName == null || displayName.isBlank()) displayName = username;
                Storage storage = plugin.storage();
                if (storage != null && !displayName.isBlank()) {
                    storage.updateLastDisplayName(normalized, username, displayName);
                }
                return new PlayerIdentity(normalized, username, displayName);
            }
        } catch (IllegalArgumentException ignored) {
        }

        Storage storage = plugin.storage();
        PlayerIdentity known = storage == null ? null : storage.findKnownPlayerByUuid(normalized);
        String username = known == null || known.username == null ? "" : known.username;
        String displayName = known == null || known.displayName == null ? "" : known.displayName;

        if (username.isBlank()) {
            try {
                OfflinePlayer offline = plugin.getServer().getOfflinePlayer(UUID.fromString(normalized));
                String offlineName = offline == null ? null : offline.getName();
                if (offlineName != null && !offlineName.isBlank()) username = offlineName;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (displayName.isBlank() && storage != null) displayName = storage.knownDisplayName(normalized);
        if (displayName.isBlank()) displayName = username;
        if (username.isBlank() && displayName.isBlank()) return null;
        if (storage != null && !username.isBlank() && !displayName.isBlank()) {
            storage.updateLastDisplayName(normalized, username, displayName);
        }
        return new PlayerIdentity(normalized, username, displayName);
    }

    @Override
    public boolean isOnline(String uuid) {
        String normalized = normalizeUuid(uuid);
        if (normalized.isBlank()) return false;
        try {
            Player player = plugin.getServer().getPlayer(UUID.fromString(normalized));
            return player != null && player.isOnline();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    public DirectMessageSettings directMessageSettings() {
        ConfigValues c = plugin.configValues();
        if (c == null) return new DirectMessageSettings(false, "sqlite", "direct-messages.db", "direct-messages.jsonl", 0, 0);
        return new DirectMessageSettings(c.directMessageEnabled, c.directMessageStorage, c.directMessageSqliteFile,
                c.directMessageJsonlFile, c.directMessageRetentionDays, c.directMessageMaxMessagesPerThread);
    }

    @Override
    public GroupChatSettings groupChatSettings() {
        ConfigValues c = plugin.configValues();
        if (c == null) return new GroupChatSettings(false, "group-messages.db", 0, 0, 0, 0, 72, 32, 500, true, true);
        return new GroupChatSettings(c.groupChatEnabled, c.groupChatSqliteFile, c.groupChatRetentionDays,
                c.groupChatMaxMessagesPerRoom, c.groupChatMaxRoomsPerUser, c.groupChatMaxMembersPerRoom,
                c.groupChatInviteExpireHours, c.groupChatMaxRoomNameLength, c.groupChatMaxMessageLength,
                c.groupChatAllowPublicRooms, c.groupChatAllowRoomPasswords);
    }

    @Override public void info(String message) { plugin.getLogger().info(message); }
    @Override public void warn(String message) { plugin.getLogger().warning(message); }

    private static String normalizeUuid(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(java.util.Locale.ROOT);
    }
}
