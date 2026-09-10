package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * DefaultFilterListInstaller는 메시지 moderation/content-filter 파이프라인의 일부다.
 * DefaultFilterListInstaller is part of the message moderation/content-filter pipeline.
 *
 * 필터 결과는 공개/DM/그룹 등 호출 위치에 따라 정책이 달라질 수 있으므로 parsing, matching, enforcement를 한 단계로 섞지 않는다.
 * Filter policy can vary by public/DM/group context, so parsing, matching, and enforcement should remain separate stages.
 */
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
