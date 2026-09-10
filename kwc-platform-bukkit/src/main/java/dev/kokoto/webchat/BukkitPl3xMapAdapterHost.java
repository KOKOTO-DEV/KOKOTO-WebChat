package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * BukkitPl3xMapAdapterHost는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * BukkitPl3xMapAdapterHost is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
import dev.kokoto.webchat.adapter.pl3xmap.Pl3xMapAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/** Bukkit resource/filesystem/logging bridge for the loader-neutral pl3xmap adapter. */
public final class BukkitPl3xMapAdapterHost implements Pl3xMapAdapterHost {
    private final KokotoWebChatPlugin plugin;
    private final CoreLogger logger;

    public BukkitPl3xMapAdapterHost(KokotoWebChatPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = CoreLogger.of(plugin.getLogger()::info, plugin.getLogger()::warning);
    }

    @Override public ConfigValues configValues() { return plugin.configValues(); }
    @Override public Path dataDirectory() { return plugin.getDataFolder().toPath(); }
    @Override public InputStream resource(String name) { return plugin.getResource(name); }
    @Override public String version() { return plugin.getDescription().getVersion(); }
    @Override public CoreLogger logger() { return logger; }
}
