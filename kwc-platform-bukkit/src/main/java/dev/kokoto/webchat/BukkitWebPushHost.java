package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * BukkitWebPushHost는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * BukkitWebPushHost is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
import java.nio.file.Path;
import java.util.Map;

/** Bukkit-side bridge for the platform-neutral WebPushManager. */
public final class BukkitWebPushHost implements WebPushHost {
    private final KokotoWebChatPlugin plugin;

    public BukkitWebPushHost(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ConfigValues config() {
        return plugin.configValues();
    }

    @Override
    public Path dataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public Account findAccountByUuid(String uuid) {
        Storage storage = plugin.storage();
        return storage == null ? null : storage.findAccountById("uuid:" + String.valueOf(uuid == null ? "" : uuid));
    }

    @Override
    public PlayerIdentity findKnownPlayerByUuid(String uuid) {
        Storage storage = plugin.storage();
        return storage == null ? null : storage.findKnownPlayerByUuid(uuid);
    }

    @Override
    public Map<String, String> webStringsFor(String language) {
        LangManager lang = plugin.langManager();
        return lang == null ? Map.of() : lang.webStringsFor(language);
    }

    @Override
    public void fine(String message) {
        plugin.getLogger().fine(message);
    }

    @Override
    public void warn(String message) {
        plugin.getLogger().warning(message);
    }
}
