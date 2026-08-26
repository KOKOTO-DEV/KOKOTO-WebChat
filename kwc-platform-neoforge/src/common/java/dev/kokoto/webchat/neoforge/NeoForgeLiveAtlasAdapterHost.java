package dev.kokoto.webchat.neoforge;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;
import dev.kokoto.webchat.adapter.liveatlas.LiveAtlasAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;

/** NeoForge host for the loader-neutral LiveAtlas filesystem adapter. */
public final class NeoForgeLiveAtlasAdapterHost implements LiveAtlasAdapterHost {
    private final KwcNeoForgeRuntime runtime;
    private final CoreLogger logger;

    public NeoForgeLiveAtlasAdapterHost(KwcNeoForgeRuntime runtime) {
        this.runtime = runtime;
        this.logger = CoreLogger.of(runtime::info, runtime::warn);
    }

    @Override public ConfigValues configValues() { return runtime.configValues(); }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }
    @Override public InputStream resource(String name) { return runtime.resource(name); }
    @Override public String version() { return runtime.version(); }
    @Override public CoreLogger logger() { return logger; }
}
