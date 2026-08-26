package dev.kokoto.webchat;

import java.nio.file.Path;
import java.util.Map;

/** Loader-neutral services required by the JDK-only Web Push engine. */
public interface WebPushHost {
    ConfigValues config();
    Path dataDirectory();
    Account findAccountByUuid(String uuid);
    PlayerIdentity findKnownPlayerByUuid(String uuid);
    Map<String, String> webStringsFor(String language);
    void fine(String message);
    void warn(String message);
}
