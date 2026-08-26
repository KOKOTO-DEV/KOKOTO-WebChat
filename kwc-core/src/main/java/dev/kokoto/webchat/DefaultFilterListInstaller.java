package dev.kokoto.webchat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/** One-time initializer for bundled editable starter filter-word lists. */
public final class DefaultFilterListInstaller {
    private DefaultFilterListInstaller() {}

    public static final String MARKER = ".kwc-defaults-initialized";
    public static final List<String> FILES = List.of("ko-KR.txt", "en-US.txt", "ja-JP.txt", "zh-CN.txt");

    @FunctionalInterface
    public interface ResourceOpener {
        InputStream open(String resourceName) throws IOException;
    }

    public record Result(boolean initializedNow, int copied, int preserved) {}

    public static Result initialize(Path dataDirectory, ResourceOpener opener) throws IOException {
        Path directory = dataDirectory.resolve(ContentFilterWordListStore.DIRECTORY);
        Path marker = directory.resolve(MARKER);
        if (Files.isRegularFile(marker)) return new Result(false, 0, 0);

        Files.createDirectories(directory);
        int copied = 0;
        int preserved = 0;
        for (String name : FILES) {
            Path active = directory.resolve(name);
            Path disabled = directory.resolve(name + ".disabled");
            if (Files.exists(active) || Files.exists(disabled)) {
                preserved++;
                continue;
            }
            String resourceName = "filter-lists-default/" + name;
            try (InputStream in = opener.open(resourceName)) {
                if (in == null) throw new IOException("Missing bundled starter filter list: " + resourceName);
                Files.copy(in, active);
                copied++;
            }
        }

        Files.writeString(marker,
                "KOKOTO WebChat starter filter lists initialized.\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return new Result(true, copied, preserved);
    }
}
