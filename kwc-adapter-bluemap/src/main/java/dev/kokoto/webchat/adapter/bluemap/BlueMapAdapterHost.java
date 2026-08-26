package dev.kokoto.webchat.adapter.bluemap;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;

import java.io.InputStream;
import java.nio.file.Path;

/** Loader-neutral host surface required by the BlueMap web adapter. */
public interface BlueMapAdapterHost {
    ConfigValues configValues();
    Path dataDirectory();
    InputStream resource(String name);
    String version();
    CoreLogger logger();
}
