package dev.kokoto.webchat.neoforge;


/* KWC 파일 안내 / KWC file guide
 * NeoForgeWebPushHost는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * NeoForgeWebPushHost is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
import dev.kokoto.webchat.*;
import java.nio.file.Path;
import java.util.Map;

public final class NeoForgeWebPushHost implements WebPushHost {
    private final KwcNeoForgeRuntime runtime;
    public NeoForgeWebPushHost(KwcNeoForgeRuntime runtime) { this.runtime = runtime; }
    @Override public ConfigValues config() { return runtime.configValues(); }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }
    @Override public Account findAccountByUuid(String uuid) { return runtime.storage() == null ? null : runtime.storage().findAccountById("uuid:" + String.valueOf(uuid == null ? "" : uuid)); }
    @Override public PlayerIdentity findKnownPlayerByUuid(String uuid) { return runtime.storage() == null ? null : runtime.storage().findKnownPlayerByUuid(uuid); }
    @Override public Map<String,String> webStringsFor(String language) { return runtime.langManager() == null ? Map.of() : runtime.langManager().webStringsFor(language); }
    @Override public void fine(String message) { runtime.debug(message); }
    @Override public void warn(String message) { runtime.warn(message); }
}
