package dev.kokoto.webchat;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/**
 * Loader-neutral Minecraft platform boundary used by the shared web/chat core.
 * Implementations own player lookup, permission checks, game-thread dispatch,
 * console commands, native chat delivery, and optional game-side integrations.
 */
public interface PlatformAdapter {
    String platformName();

    String minecraftVersion();

    Path dataDirectory();

    Collection<PlatformPlayer> onlinePlayers();

    Optional<PlatformPlayer> onlinePlayer(UUID uuid);

    boolean hasPermission(UUID uuid, String permission);

    boolean isMainThread();

    void runMainThread(Runnable task);

    boolean dispatchConsoleCommand(String command);

    Set<String> knownPlayerNames();

    /**
     * Names that guests must not impersonate. Implementations should include
     * real usernames plus known display/custom-name aliases where available.
     */
    default Set<String> knownPlayerNameAliases() {
        return knownPlayerNames();
    }

    void sendPlainMessage(UUID uuid, String message);

    void broadcastPlainMessage(String message);

    void sendInteractiveMessage(Collection<UUID> recipients, PlatformGameMessage message);

    void broadcastInteractiveMessage(PlatformGameMessage message);

    /** Optional platform/plugin integration snapshot used by ImageEmojis rendering. */
    Map<String, String> imageEmojiRuntimeSymbols();
}
