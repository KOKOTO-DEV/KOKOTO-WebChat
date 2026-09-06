package dev.kokoto.webchat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Loader-neutral Modrinth update checker used by Fabric, NeoForge and Forge.
 * Bukkit keeps its native clickable-component notifier. Starting with KWC 5.2.0,
 * update checks use only the canonical KOKOTO WebChat publication addresses.
 */
public final class PortableUpdateChecker implements AutoCloseable {
    private static final UpdateSource PRIMARY_SOURCE = new UpdateSource(
            URI.create("https://api.modrinth.com/v2/project/kokoto-webchat/version"),
            "https://modrinth.com/plugin/kokoto-webchat",
            "https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat",
            "kokoto-webchat");
    private static final long INITIAL_DELAY_SECONDS = 3L;
    private static final long CHECK_INTERVAL_SECONDS = 12L * 60L * 60L;
    private static final long JOIN_REFRESH_MIN_INTERVAL_MILLIS = 60_000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final String currentVersion;
    private final PlatformAdapter platform;
    private final WebChatLanguage language;
    private final Consumer<String> info;
    private final Consumer<String> warn;
    private final HttpClient httpClient;
    private final ScheduledExecutorService executor;
    private final OperationalIssueTracker issues;
    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean requestRunning = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile UpdateInfo availableUpdate;
    private volatile UpdateSource activeSource = PRIMARY_SOURCE;
    private volatile long lastAttemptMillis;
    private volatile String lastLoggedVersion = "";

    public PortableUpdateChecker(String currentVersion,
                                 PlatformAdapter platform,
                                 WebChatLanguage language,
                                 Consumer<String> info,
                                 Consumer<String> warn) {
        this.currentVersion = currentVersion == null ? "" : currentVersion.trim();
        this.platform = platform;
        this.language = language;
        this.info = info == null ? ignored -> {} : info;
        this.warn = warn == null ? ignored -> {} : warn;
        this.issues = new OperationalIssueTracker(this.info, this.warn);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kwc-update-check");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (closed.get()) return;
        executor.scheduleWithFixedDelay(this::checkNowSafe,
                INITIAL_DELAY_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** Called by the platform's normal player-join callback. */
    public void onPlayerJoin(UUID playerId) {
        if (playerId == null || closed.get()) return;
        notifyPlayer(playerId);
        long now = System.currentTimeMillis();
        if (now - lastAttemptMillis >= JOIN_REFRESH_MIN_INTERVAL_MILLIS) {
            executor.execute(this::checkNowSafe);
        }
    }

    private void checkNowSafe() {
        if (closed.get() || !requestRunning.compareAndSet(false, true)) return;
        lastAttemptMillis = System.currentTimeMillis();
        try {
            SourceResult result = querySource(PRIMARY_SOURCE);
            if (!result.usable()) {
                String detail = "Modrinth source unavailable: " + result.detail;
                issues.failed("update-check", detail,
                        "KOKOTO WebChat update check failed: " + detail
                                + ". Current version=" + currentVersion
                                + ", source=" + PRIMARY_SOURCE.api);
                return;
            }

            activeSource = result.source;
            issues.recovered("update-check", "KOKOTO WebChat update check recovered via " + activeSource.slug + ".");
            UpdateInfo newest = result.update;
            if (compareVersions(newest.version, currentVersion) <= 0) {
                availableUpdate = null;
                return;
            }

            UpdateInfo previous = availableUpdate;
            availableUpdate = newest;
            if (previous == null || !newest.version.equals(previous.version)) notifiedPlayers.clear();
            if (!newest.version.equals(lastLoggedVersion)) {
                lastLoggedVersion = newest.version;
                info.accept("KOKOTO WebChat update available: " + newest.version
                        + " (current: " + currentVersion + ") - " + activeSource.modrinthPage);
            }

            for (PlatformPlayer player : platform.onlinePlayers()) {
                if (player != null) notifyPlayer(player.uuid());
            }
        } finally {
            requestRunning.set(false);
        }
    }

    private SourceResult querySource(UpdateSource source) {
        try {
            HttpRequest request = HttpRequest.newBuilder(source.api)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "KOKOTO-DEV/KOKOTO-WebChat/" + currentVersion + " (" + source.modrinthPage + ")")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return SourceResult.failed(source, source.slug + " returned HTTP " + response.statusCode());
            }
            UpdateInfo newest = newestRelease(response.body());
            if (newest == null) return SourceResult.failed(source, source.slug + " contained no listed release version");
            return SourceResult.ok(source, newest);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return SourceResult.failed(source, source.slug + " interrupted");
        } catch (Exception ex) {
            String detail = ex.getClass().getSimpleName()
                    + (ex.getMessage() == null || ex.getMessage().isBlank() ? "" : ": " + ex.getMessage());
            return SourceResult.failed(source, source.slug + " " + detail);
        }
    }

    private void notifyPlayer(UUID playerId) {
        UpdateInfo update = availableUpdate;
        if (update == null || playerId == null || closed.get()) return;
        if (platform.onlinePlayer(playerId).isEmpty()) return;
        if (!platform.hasPermission(playerId, "kwc.update.notify")) return;
        if (!notifiedPlayers.add(playerId)) return;

        String notice = language == null
                ? "A new version {latest} is available. Current: {current}"
                : language.text("update.available", "A new version {latest} is available. Current: {current}",
                Map.of("latest", update.version, "current", currentVersion));
        if (language == null) {
            notice = notice.replace("{latest}", update.version).replace("{current}", currentVersion);
        }
        String modrinthLabel = language == null ? "Modrinth" : language.text("update.modrinth", "Modrinth");
        String curseForgeLabel = language == null ? "CurseForge" : language.text("update.curseforge", "CurseForge");
        UpdateSource source = activeSource == null ? PRIMARY_SOURCE : activeSource;
        platform.sendPlainMessage(playerId, "[KOKOTO WebChat] " + notice
                + " [" + modrinthLabel + "] " + source.modrinthPage
                + " [" + curseForgeLabel + "] " + source.curseForgePage);
    }

    static UpdateInfo newestRelease(String json) {
        UpdateInfo newest = null;
        for (String object : topLevelObjects(json)) {
            String type = topLevelString(object, "version_type");
            if (!"release".equalsIgnoreCase(type)) continue;
            String status = topLevelString(object, "status");
            if (!status.isBlank() && !"listed".equalsIgnoreCase(status)) continue;
            String version = topLevelString(object, "version_number").trim();
            if (version.isBlank()) continue;
            UpdateInfo candidate = new UpdateInfo(version);
            if (newest == null || compareVersions(candidate.version, newest.version) > 0) newest = candidate;
        }
        return newest;
    }

    private static List<String> topLevelObjects(String json) {
        List<String> out = new ArrayList<>();
        if (json == null) return out;
        boolean inString = false, escaped = false;
        int depth = 0, start = -1;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') inString = false;
                continue;
            }
            if (ch == '"') { inString = true; continue; }
            if (ch == '{') { if (depth == 0) start = i; depth++; }
            else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) { out.add(json.substring(start, i + 1)); start = -1; }
            }
        }
        return out;
    }

    private static String topLevelString(String object, String wantedKey) {
        if (object == null || wantedKey == null) return "";
        int depth = 0;
        boolean inString = false, escaped = false, readingKey = false;
        StringBuilder token = new StringBuilder();
        String pendingKey = null;
        for (int i = 0; i < object.length(); i++) {
            char ch = object.charAt(i);
            if (inString) {
                if (escaped) { token.append(unescapeChar(ch)); escaped = false; }
                else if (ch == '\\') escaped = true;
                else if (ch == '"') {
                    inString = false;
                    if (readingKey) pendingKey = token.toString();
                    else if (depth == 1 && wantedKey.equals(pendingKey)) return token.toString();
                    token.setLength(0);
                } else token.append(ch);
                continue;
            }
            if (ch == '{' || ch == '[') { depth++; continue; }
            if (ch == '}' || ch == ']') { depth--; continue; }
            if (depth != 1) continue;
            if (ch == '"') {
                int j = i - 1;
                while (j >= 0 && Character.isWhitespace(object.charAt(j))) j--;
                readingKey = j < 0 || object.charAt(j) == '{' || object.charAt(j) == ',';
                inString = true;
                token.setLength(0);
            }
        }
        return "";
    }

    private static char unescapeChar(char ch) {
        return switch (ch) {
            case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case 'b' -> '\b'; case 'f' -> '\f'; default -> ch;
        };
    }

    static int compareVersions(String left, String right) {
        ParsedVersion a = parseVersion(left), b = parseVersion(right);
        int length = Math.max(a.core.length, b.core.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.core.length ? a.core[i] : 0, bv = i < b.core.length ? b.core[i] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }

        // SemVer ordering: a release is newer than a prerelease of the same core.
        if (a.preRelease.isEmpty() && b.preRelease.isEmpty()) return 0;
        if (a.preRelease.isEmpty()) return 1;
        if (b.preRelease.isEmpty()) return -1;

        int identifiers = Math.max(a.preRelease.size(), b.preRelease.size());
        for (int i = 0; i < identifiers; i++) {
            if (i >= a.preRelease.size()) return -1;
            if (i >= b.preRelease.size()) return 1;
            String ai = a.preRelease.get(i), bi = b.preRelease.get(i);
            boolean an = ai.chars().allMatch(Character::isDigit);
            boolean bn = bi.chars().allMatch(Character::isDigit);
            if (an && bn) {
                try {
                    int cmp = Long.compare(Long.parseLong(ai), Long.parseLong(bi));
                    if (cmp != 0) return cmp;
                } catch (NumberFormatException ignored) {
                    int cmp = ai.compareTo(bi);
                    if (cmp != 0) return cmp;
                }
            } else if (an != bn) {
                // Numeric prerelease identifiers have lower precedence than non-numeric identifiers.
                return an ? -1 : 1;
            } else {
                int cmp = ai.compareTo(bi);
                if (cmp != 0) return cmp;
            }
        }
        return 0;
    }

    private static ParsedVersion parseVersion(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("v")) value = value.substring(1);
        int build = value.indexOf('+');
        if (build >= 0) value = value.substring(0, build);

        String pre = "";
        int suffix = value.indexOf('-');
        if (suffix >= 0) {
            pre = value.substring(suffix + 1);
            value = value.substring(0, suffix);
        }

        String[] parts = value.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9].*$", "");
            try { numbers[i] = digits.isBlank() ? 0 : Integer.parseInt(digits); }
            catch (NumberFormatException ignored) { numbers[i] = 0; }
        }

        List<String> preRelease = new ArrayList<>();
        if (!pre.isBlank()) {
            for (String item : pre.split("\\.")) {
                if (!item.isBlank()) preRelease.add(item);
            }
        }
        return new ParsedVersion(numbers, preRelease);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        executor.shutdownNow();
        notifiedPlayers.clear();
        availableUpdate = null;
    }

    static final class ParsedVersion {
        final int[] core;
        final List<String> preRelease;
        ParsedVersion(int[] core, List<String> preRelease) {
            this.core = core == null ? new int[0] : core;
            this.preRelease = preRelease == null ? List.of() : List.copyOf(preRelease);
        }
    }
    static final class UpdateSource {
        final URI api; final String modrinthPage; final String curseForgePage; final String slug;
        UpdateSource(URI api, String modrinthPage, String curseForgePage, String slug) {
            this.api = api; this.modrinthPage = modrinthPage; this.curseForgePage = curseForgePage; this.slug = slug;
        }
    }
    static final class SourceResult {
        final UpdateSource source; final UpdateInfo update; final String detail;
        private SourceResult(UpdateSource source, UpdateInfo update, String detail) {
            this.source = source; this.update = update; this.detail = detail == null ? "" : detail;
        }
        static SourceResult ok(UpdateSource source, UpdateInfo update) { return new SourceResult(source, update, ""); }
        static SourceResult failed(UpdateSource source, String detail) { return new SourceResult(source, null, detail); }
        boolean usable() { return source != null && update != null; }
    }
    static final class UpdateInfo {
        final String version;
        UpdateInfo(String version) { this.version = version; }
    }
}
