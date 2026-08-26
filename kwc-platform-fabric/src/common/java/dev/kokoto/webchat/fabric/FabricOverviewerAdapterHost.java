package dev.kokoto.webchat.fabric;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;
import dev.kokoto.webchat.adapter.overviewer.OverviewerAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;

/** Fabric host for the loader-neutral Minecraft Overviewer filesystem adapter. */
public final class FabricOverviewerAdapterHost implements OverviewerAdapterHost {
    private final KwcFabricRuntime runtime;
    private final CoreLogger logger;

    public FabricOverviewerAdapterHost(KwcFabricRuntime runtime) {
        this.runtime = runtime;
        this.logger = CoreLogger.of(runtime::info, runtime::warn);
    }

    @Override public ConfigValues configValues() { return runtime.configValues(); }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }
    @Override public InputStream resource(String name) { return runtime.resource(name); }
    @Override public String version() { return runtime.version(); }
    @Override public CoreLogger logger() { return logger; }
}
