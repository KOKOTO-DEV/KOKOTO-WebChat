package dev.kokoto.webchat.fabric;


/* KWC 파일 안내 / KWC file guide
 * FabricConversationStoreHost는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * FabricConversationStoreHost is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
import dev.kokoto.webchat.*;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public final class FabricConversationStoreHost implements ConversationStoreHost {
    private final KwcFabricRuntime runtime;
    public FabricConversationStoreHost(KwcFabricRuntime runtime) { this.runtime = runtime; }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }

    @Override
    public PlayerIdentity resolveIdentity(String uuid) {
        String normalized = String.valueOf(uuid == null ? "" : uuid).trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return new PlayerIdentity("", "", "");
        try {
            ServerPlayer online = runtime.server().getPlayerList().getPlayer(UUID.fromString(normalized));
            if (online != null) {
                String username = FabricCompat.profileName(online);
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
