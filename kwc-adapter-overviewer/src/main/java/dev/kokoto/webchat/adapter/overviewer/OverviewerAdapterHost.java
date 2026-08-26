package dev.kokoto.webchat.adapter.overviewer;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;

import java.io.InputStream;
import java.nio.file.Path;

/** Loader-neutral filesystem/resource surface required by the Minecraft Overviewer web adapter. */
public interface OverviewerAdapterHost {
    ConfigValues configValues();
    Path dataDirectory();
    InputStream resource(String name);
    String version();
    CoreLogger logger();
}
