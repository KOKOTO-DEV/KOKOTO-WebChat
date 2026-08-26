package dev.kokoto.webchat.neoforge;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;
import dev.kokoto.webchat.adapter.squaremap.SquaremapAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;

/** NeoForge resource/filesystem/logging bridge for the loader-neutral squaremap adapter. */
public final class NeoForgeSquaremapAdapterHost implements SquaremapAdapterHost {
    private final KwcNeoForgeRuntime runtime;
    private final CoreLogger logger;

    public NeoForgeSquaremapAdapterHost(KwcNeoForgeRuntime runtime) {
        this.runtime = runtime;
        this.logger = CoreLogger.of(runtime::info, runtime::warn);
    }

    @Override public ConfigValues configValues() { return runtime.configValues(); }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }
    @Override public InputStream resource(String name) { return runtime.resource(name); }
    @Override public String version() { return runtime.version(); }
    @Override public CoreLogger logger() { return logger; }
}
