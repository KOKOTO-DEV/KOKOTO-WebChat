package dev.kokoto.webchat.adapter.dynmap;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;

import java.io.InputStream;
import java.nio.file.Path;

/** Loader-neutral filesystem/resource surface required by the Dynmap web adapter. */
public interface DynmapAdapterHost {
    ConfigValues configValues();
    Path dataDirectory();
    InputStream resource(String name);
    String version();
    CoreLogger logger();
}
