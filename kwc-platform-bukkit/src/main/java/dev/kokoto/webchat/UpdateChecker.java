package dev.kokoto.webchat;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Lightweight release update notification backed by Modrinth's public API.
 * Only the master switch is configurable; request cadence and presentation are
 * intentionally internal defaults so config.yml stays compact.
 */
public final class UpdateChecker implements Listener, AutoCloseable {
    private static final UpdateSource PRIMARY_SOURCE = new UpdateSource(
            URI.create("https://api.modrinth.com/v2/project/kokoto-webchat/version"),
            "https://modrinth.com/plugin/kokoto-webchat",
            "https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat",
            "kokoto-webchat");
    private static final UpdateSource LEGACY_SOURCE = new UpdateSource(
            URI.create("https://api.modrinth.com/v2/project/bluemapwebchat/version"),
            "https://modrinth.com/plugin/bluemapwebchat",
            "https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat",
            "bluemapwebchat");
    private static final long INITIAL_DELAY_TICKS = 60L;
    private static final long CHECK_INTERVAL_TICKS = 12L * 60L * 60L * 20L;
    private static final long JOIN_NOTICE_DELAY_TICKS = 60L;
    private static final long JOIN_REFRESH_MIN_INTERVAL_MILLIS = 60_000L;
    private static final long FAILURE_LOG_REPEAT_MILLIS = 30L * 60L * 1000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final KokotoWebChatPlugin plugin;
    private final HttpClient httpClient;
    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean requestRunning = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile UpdateInfo availableUpdate;
    private volatile String lastLoggedVersion = "";
    private volatile long lastAttemptMillis;
    private volatile String lastFailureMessage = "";
    private volatile UpdateSource activeSource = PRIMARY_SOURCE;
    private volatile long lastFailureLogMillis;
    private BukkitTask checkTask;

    public UpdateChecker(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void start() {
        ConfigValues config = plugin.configValues();
        if (config == null || !config.updateCheckEnabled || closed.get()) return;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        checkTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::checkNow, INITIAL_DELAY_TICKS, CHECK_INTERVAL_TICKS);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!canReceiveNotice(player)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!canReceiveNotice(player) || closed.get()) return;
            // Show a cached notice immediately when available, then refresh the public
            // release state so an administrator login can discover a release published
            // after the server's previous scheduled check.
            notifyPlayer(player);
            requestJoinRefresh();
        }, JOIN_NOTICE_DELAY_TICKS);
    }

    private void requestJoinRefresh() {
        if (closed.get()) return;
        long now = System.currentTimeMillis();
        if (now - lastAttemptMillis < JOIN_REFRESH_MIN_INTERVAL_MILLIS) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::checkNow);
    }

    private void checkNow() {
        if (closed.get() || !requestRunning.compareAndSet(false, true)) return;
        lastAttemptMillis = System.currentTimeMillis();
        try {
            String current = plugin.getDescription().getVersion();
            SourceResult result = querySource(PRIMARY_SOURCE, current);
            if (!result.usable()) {
                SourceResult legacy = querySource(LEGACY_SOURCE, current);
                if (legacy.usable()) {
                    result = legacy;
                } else {
                    warnCheckFailure("Modrinth sources unavailable: "
                            + result.detail + "; fallback " + legacy.detail, null);
                    return;
                }
            }

            UpdateInfo newest = result.update;
            activeSource = result.source;
            clearFailureState();
            if (compareVersions(newest.version, current) <= 0) {
                availableUpdate = null;
                return;
            }

            UpdateInfo previous = availableUpdate;
            availableUpdate = newest;
            if (previous == null || !newest.version.equals(previous.version)) {
                notifiedPlayers.clear();
            }
            if (!newest.version.equals(lastLoggedVersion)) {
                lastLoggedVersion = newest.version;
                plugin.getLogger().info("KOKOTO WebChat update available: " + newest.version
                        + " (current: " + current + ") - " + activeSource.modrinthPage);
            }

            if (!closed.get()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (closed.get()) return;
                    for (Player player : Bukkit.getOnlinePlayers()) notifyPlayer(player);
                });
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            warnCheckFailure("update check was interrupted", ex);
        } catch (Exception ex) {
            warnCheckFailure(ex.getClass().getSimpleName()
                    + (ex.getMessage() == null || ex.getMessage().isBlank() ? "" : ": " + ex.getMessage()), ex);
        } finally {
            requestRunning.set(false);
        }
    }

    private SourceResult querySource(UpdateSource source, String current) throws InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder(source.api)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "KOKOTO-DEV/KOKOTO-WebChat/" + current + " (" + source.modrinthPage + ")")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return SourceResult.failed(source, source.slug + " returned HTTP " + response.statusCode());
            }
            UpdateInfo newest = newestRelease(response.body());
            if (newest == null) {
                return SourceResult.failed(source, source.slug + " contained no listed release version");
            }
            return SourceResult.ok(source, newest);
        } catch (InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            String detail = ex.getClass().getSimpleName()
                    + (ex.getMessage() == null || ex.getMessage().isBlank() ? "" : ": " + ex.getMessage());
            return SourceResult.failed(source, source.slug + " " + detail);
        }
    }

    private boolean canReceiveNotice(Player player) {
        return player != null && player.isOnline()
                && (player.isOp() || PermissionCompat.has(player, "kwc.update.notify"));
    }

    private void clearFailureState() {
        lastFailureMessage = "";
        lastFailureLogMillis = 0L;
    }

    private void warnCheckFailure(String detail, Throwable error) {
        if (closed.get()) return;
        String safeDetail = detail == null || detail.isBlank() ? "unknown error" : detail;
        long now = System.currentTimeMillis();
        boolean changed = !safeDetail.equals(lastFailureMessage);
        if (!changed && now - lastFailureLogMillis < FAILURE_LOG_REPEAT_MILLIS) return;

        lastFailureMessage = safeDetail;
        lastFailureLogMillis = now;
        String message = "KOKOTO WebChat update check failed: " + safeDetail
                + ". Current version=" + plugin.getDescription().getVersion()
                + ", sources=" + PRIMARY_SOURCE.api + " -> " + LEGACY_SOURCE.api;
        if (error == null) plugin.getLogger().warning(message);
        else plugin.getLogger().log(Level.WARNING, message, error);
    }

    private void notifyPlayer(Player player) {
        UpdateInfo update = availableUpdate;
        if (update == null || !canReceiveNotice(player) || !notifiedPlayers.add(player.getUniqueId())) return;

        LangManager lang = plugin.langManager();
        String current = plugin.getDescription().getVersion();
        String notice = lang == null
                ? "A new version {latest} is available. Current: {current}"
                : lang.text("update.available", "A new version {latest} is available. Current: {current}",
                Map.of("latest", update.version, "current", current));
        String openHint = lang == null
                ? "Open download page"
                : lang.text("update.openDownload", "Open download page");
        String modrinthLabel = lang == null ? "Modrinth" : lang.text("update.modrinth", "Modrinth");
        String curseForgeLabel = lang == null ? "CurseForge" : lang.text("update.curseforge", "CurseForge");

        TextComponent line = new TextComponent("");
        line.addExtra(new TextComponent(ChatColor.AQUA + "[KOKOTO WebChat] " + ChatColor.YELLOW + notice + " "));
        UpdateSource source = activeSource == null ? PRIMARY_SOURCE : activeSource;
        line.addExtra(linkButton("[" + modrinthLabel + "]", source.modrinthPage, openHint));
        line.addExtra(new TextComponent(" "));
        line.addExtra(linkButton("[" + curseForgeLabel + "]", source.curseForgePage, openHint));
        player.spigot().sendMessage(line);
    }

    private TextComponent linkButton(String label, String url, String hover) {
        TextComponent component = new TextComponent(ChatColor.AQUA + "" + ChatColor.UNDERLINE + label);
        component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(hover).color(ChatColor.GRAY).create()));
        return component;
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
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') inString = false;
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    private static String topLevelString(String object, String wantedKey) {
        if (object == null || wantedKey == null) return "";
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        StringBuilder token = new StringBuilder();
        String pendingKey = null;
        boolean readingKey = false;

        for (int i = 0; i < object.length(); i++) {
            char ch = object.charAt(i);
            if (inString) {
                if (escaped) {
                    token.append(unescapeChar(ch));
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                    if (readingKey) pendingKey = token.toString();
                    else if (depth == 1 && wantedKey.equals(pendingKey)) return token.toString();
                    token.setLength(0);
                } else {
                    token.append(ch);
                }
                continue;
            }

            if (ch == '{' || ch == '[') {
                depth++;
                continue;
            }
            if (ch == '}' || ch == ']') {
                depth--;
                continue;
            }
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
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            default -> ch;
        };
    }

    static int compareVersions(String left, String right) {
        return PortableUpdateChecker.compareVersions(left, right);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        HandlerList.unregisterAll(this);
        notifiedPlayers.clear();
        availableUpdate = null;
    }

    static final class UpdateSource {
        final URI api;
        final String modrinthPage;
        final String curseForgePage;
        final String slug;

        UpdateSource(URI api, String modrinthPage, String curseForgePage, String slug) {
            this.api = api;
            this.modrinthPage = modrinthPage;
            this.curseForgePage = curseForgePage;
            this.slug = slug;
        }
    }

    static final class SourceResult {
        final UpdateSource source;
        final UpdateInfo update;
        final String detail;

        private SourceResult(UpdateSource source, UpdateInfo update, String detail) {
            this.source = source;
            this.update = update;
            this.detail = detail == null ? "" : detail;
        }

        static SourceResult ok(UpdateSource source, UpdateInfo update) {
            return new SourceResult(source, update, "");
        }

        static SourceResult failed(UpdateSource source, String detail) {
            return new SourceResult(source, null, detail);
        }

        boolean usable() {
            return source != null && update != null;
        }
    }

    static final class UpdateInfo {
        final String version;

        UpdateInfo(String version) {
            this.version = version;
        }
    }
}
