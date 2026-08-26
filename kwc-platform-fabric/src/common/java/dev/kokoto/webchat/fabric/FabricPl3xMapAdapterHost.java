package dev.kokoto.webchat.fabric;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;
import dev.kokoto.webchat.adapter.pl3xmap.Pl3xMapAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;

/** Fabric resource/filesystem/logging bridge for the loader-neutral pl3xmap adapter. */
public final class FabricPl3xMapAdapterHost implements Pl3xMapAdapterHost {
    private final KwcFabricRuntime runtime;
    private final CoreLogger logger;

    public FabricPl3xMapAdapterHost(KwcFabricRuntime runtime) {
        this.runtime = runtime;
        this.logger = CoreLogger.of(runtime::info, runtime::warn);
    }

    @Override public ConfigValues configValues() { return runtime.configValues(); }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }
    @Override public InputStream resource(String name) { return runtime.resource(name); }
    @Override public String version() { return runtime.version(); }
    @Override public CoreLogger logger() { return logger; }
}
