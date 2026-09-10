package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * PlatformAdapter는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * PlatformAdapter is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
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
