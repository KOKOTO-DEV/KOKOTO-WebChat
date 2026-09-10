package dev.kokoto.webchat.adapter.pl3xmap;


/* KWC 파일 안내 / KWC file guide
 * Pl3xMapAdapterHost는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * Pl3xMapAdapterHost is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;

import java.io.InputStream;
import java.nio.file.Path;

/** Loader-neutral filesystem/resource surface required by the pl3xmap web adapter. */
public interface Pl3xMapAdapterHost {
    ConfigValues configValues();
    Path dataDirectory();
    InputStream resource(String name);
    String version();
    CoreLogger logger();
}
