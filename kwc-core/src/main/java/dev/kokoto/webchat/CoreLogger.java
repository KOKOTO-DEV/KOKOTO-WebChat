package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * core가 특정 loader logger에 의존하지 않고 info/warn/error를 기록하도록 하는 최소 logging 계약이다.
 * Minimal logging contract allowing core to emit info/warn/error without depending on a loader-specific logger.
 *
 * 민감한 token, shared secret, password, raw auth code는 오류 진단을 위해서도 로그에 남기지 않는다.
 * Never log sensitive tokens, shared secrets, passwords, or raw authentication codes even for diagnostics.
 */
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
