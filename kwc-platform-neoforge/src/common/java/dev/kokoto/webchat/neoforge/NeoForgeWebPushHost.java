package dev.kokoto.webchat.neoforge;

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
