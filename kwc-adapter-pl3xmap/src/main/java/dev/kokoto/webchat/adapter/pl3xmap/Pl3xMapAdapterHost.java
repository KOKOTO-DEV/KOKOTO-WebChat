package dev.kokoto.webchat.adapter.pl3xmap;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;

import java.io.InputStream;
import java.nio.file.Path;

/** Loader-neutral filesystem/resource surface required by the pl3xmap web adapter. */
public interface Pl3xMapAdapterHost {
    ConfigValues configValues();
    Path dataDirectory();
    InputStream resource(String name);
    String version();
    CoreLogger logger();
}
