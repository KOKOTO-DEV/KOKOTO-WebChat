package dev.kokoto.webchat.forge;


/* KWC 파일 안내 / KWC file guide
 * ForgeUnminedAdapterHost는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * ForgeUnminedAdapterHost is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;
import dev.kokoto.webchat.adapter.unmined.UnminedAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;

/** Forge host for the loader-neutral uNmINeD filesystem adapter. */
public final class ForgeUnminedAdapterHost implements UnminedAdapterHost {
    private final KwcForgeRuntime runtime;
    private final CoreLogger logger;

    public ForgeUnminedAdapterHost(KwcForgeRuntime runtime) {
        this.runtime = runtime;
        this.logger = CoreLogger.of(runtime::info, runtime::warn);
    }

    @Override public ConfigValues configValues() { return runtime.configValues(); }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }
    @Override public InputStream resource(String name) { return runtime.resource(name); }
    @Override public String version() { return runtime.version(); }
    @Override public CoreLogger logger() { return logger; }
}
