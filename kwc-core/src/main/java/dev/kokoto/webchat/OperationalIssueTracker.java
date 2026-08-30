package dev.kokoto.webchat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared suppression policy for recurring operational/network failures.
 *
 * <p>The first occurrence of an issue is logged immediately. Repeated occurrences
 * with the same key and fingerprint are suppressed until the repeat interval has
 * elapsed, then one summary warning is emitted. A changed fingerprint is treated
 * as a new failure and is logged immediately. Call {@link #recovered(String, String)}
 * when the operation succeeds again so a long-running failure can emit one recovery
 * notice and future failures start a fresh cycle.</p>
 *
 * <p>This class deliberately does not decide whether an HTTP status is an error or
 * how/when an operation should retry. Functional retry/backoff policy stays in the
 * caller; this class only prevents identical console failures from accumulating.</p>
 */
public final class OperationalIssueTracker {
    public static final long DEFAULT_REPEAT_MILLIS = 30L * 60L * 1000L;
    private static final long DEFAULT_STALE_MILLIS = 24L * 60L * 60L * 1000L;
    private static final int DEFAULT_MAX_KEYS = 512;

    private final Consumer<String> info;
    private final Consumer<String> warn;
    private final long repeatMillis;
    private final long staleMillis;
    private final int maxKeys;
    private final LinkedHashMap<String, State> states = new LinkedHashMap<>();

    public OperationalIssueTracker(Consumer<String> info, Consumer<String> warn) {
        this(info, warn, DEFAULT_REPEAT_MILLIS, DEFAULT_STALE_MILLIS, DEFAULT_MAX_KEYS);
    }

    public OperationalIssueTracker(Consumer<String> info, Consumer<String> warn, long repeatMillis) {
        this(info, warn, repeatMillis, DEFAULT_STALE_MILLIS, DEFAULT_MAX_KEYS);
    }

    OperationalIssueTracker(Consumer<String> info, Consumer<String> warn,
                            long repeatMillis, long staleMillis, int maxKeys) {
        this.info = info == null ? ignored -> { } : info;
        this.warn = warn == null ? ignored -> { } : warn;
        this.repeatMillis = Math.max(1_000L, repeatMillis);
        this.staleMillis = Math.max(this.repeatMillis, staleMillis);
        this.maxKeys = Math.max(16, maxKeys);
    }

    /**
     * Records a failure. Returns true only when a console warning was emitted.
     */
    public synchronized boolean failed(String key, String fingerprint, String message) {
        long now = System.currentTimeMillis();
        cleanup(now);
        String safeKey = normalizeKey(key);
        String safeFingerprint = normalizeFingerprint(fingerprint);
        String safeMessage = normalizeMessage(message);

        State state = states.get(safeKey);
        if (state == null || !Objects.equals(state.fingerprint, safeFingerprint)) {
            state = new State(safeFingerprint, now, now, 0L);
            putBounded(safeKey, state);
            warn.accept(safeMessage);
            return true;
        }

        state.lastSeenMillis = now;
        if (now - state.lastLoggedMillis < repeatMillis) {
            state.suppressed++;
            return false;
        }

        long repeated = state.suppressed + 1L;
        state.suppressed = 0L;
        state.lastLoggedMillis = now;
        warn.accept(safeMessage + " (same issue repeated " + repeated + " time(s) since the previous log; intermediate logs were suppressed)");
        return true;
    }

    /**
     * Clears an issue after a successful operation. If a failure state existed,
     * exactly one recovery notice is emitted so the console records the transition
     * back to healthy even when the failure occurred only once.
     */
    public synchronized boolean recovered(String key, String message) {
        String safeKey = normalizeKey(key);
        State state = states.remove(safeKey);
        if (state == null) return false;
        String safeMessage = normalizeMessage(message);
        if (state.suppressed > 0L) {
            info.accept(safeMessage + " (" + state.suppressed + " repeated failure(s) were suppressed before recovery)");
        } else {
            info.accept(safeMessage);
        }
        return true;
    }

    public synchronized void clear(String key) {
        states.remove(normalizeKey(key));
    }

    public synchronized void clearAll() {
        states.clear();
    }

    synchronized int trackedIssueCount() {
        return states.size();
    }

    private void cleanup(long now) {
        states.entrySet().removeIf(entry -> now - entry.getValue().lastSeenMillis > staleMillis);
    }

    private void putBounded(String key, State state) {
        if (!states.containsKey(key) && states.size() >= maxKeys) {
            String oldest = states.keySet().iterator().next();
            states.remove(oldest);
        }
        states.put(key, state);
    }

    private static String normalizeKey(String value) {
        String out = value == null ? "" : value.trim();
        return out.isEmpty() ? "unknown" : out;
    }

    private static String normalizeFingerprint(String value) {
        String out = value == null ? "" : value.trim();
        return out.isEmpty() ? "unknown" : out;
    }

    private static String normalizeMessage(String value) {
        String out = value == null ? "" : value.trim();
        return out.isEmpty() ? "Operational failure" : out;
    }

    private static final class State {
        final String fingerprint;
        long lastLoggedMillis;
        long lastSeenMillis;
        long suppressed;

        State(String fingerprint, long lastLoggedMillis, long lastSeenMillis, long suppressed) {
            this.fingerprint = fingerprint;
            this.lastLoggedMillis = lastLoggedMillis;
            this.lastSeenMillis = lastSeenMillis;
            this.suppressed = suppressed;
        }
    }
}
