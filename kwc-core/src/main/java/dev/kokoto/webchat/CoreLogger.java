package dev.kokoto.webchat;

import java.util.Objects;
import java.util.function.Consumer;

/** Platform-neutral logging boundary used by core services. */
public interface CoreLogger {
    void info(String message);
    void warn(String message);

    static CoreLogger of(Consumer<String> info, Consumer<String> warn) {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(warn, "warn");
        return new CoreLogger() {
            @Override public void info(String message) { info.accept(message); }
            @Override public void warn(String message) { warn.accept(message); }
        };
    }
}
