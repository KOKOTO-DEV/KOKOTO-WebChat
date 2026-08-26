package dev.kokoto.webchat.forge;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;
import dev.kokoto.webchat.adapter.dynmap.DynmapAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;

/** Forge resource/filesystem/logging bridge for the loader-neutral Dynmap adapter. */
public final class ForgeDynmapAdapterHost implements DynmapAdapterHost {
    private final KwcForgeRuntime runtime;
    private final CoreLogger logger;

    public ForgeDynmapAdapterHost(KwcForgeRuntime runtime) {
        this.runtime = runtime;
        this.logger = CoreLogger.of(runtime::info, runtime::warn);
    }

    @Override public ConfigValues configValues() { return runtime.configValues(); }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }
    @Override public InputStream resource(String name) { return runtime.resource(name); }
    @Override public String version() { return runtime.version(); }
    @Override public CoreLogger logger() { return logger; }
}
