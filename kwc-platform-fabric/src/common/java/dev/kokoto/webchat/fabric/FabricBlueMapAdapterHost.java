package dev.kokoto.webchat.fabric;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;
import dev.kokoto.webchat.adapter.bluemap.BlueMapAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;

/** Fabric host for the BlueMapAPI-backed web adapter. */
public final class FabricBlueMapAdapterHost implements BlueMapAdapterHost {
    private final KwcFabricRuntime runtime;
    private final CoreLogger logger;

    public FabricBlueMapAdapterHost(KwcFabricRuntime runtime) {
        this.runtime = runtime;
        this.logger = CoreLogger.of(runtime::info, runtime::warn);
    }

    @Override public ConfigValues configValues() { return runtime.configValuesForMapAdapter(); }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }
    @Override public InputStream resource(String name) { return runtime.resource(name); }
    @Override public String version() { return runtime.version(); }
    @Override public CoreLogger logger() { return logger; }
}
