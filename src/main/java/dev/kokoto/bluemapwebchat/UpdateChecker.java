package dev.kokoto.bluemapwebchat;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Lightweight release update notification backed by Modrinth's public API.
 * Only the master switch is configurable; request cadence and presentation are
 * intentionally internal defaults so config.yml stays compact.
 */
public final class UpdateChecker implements Listener, AutoCloseable {
    private static final URI MODRINTH_API = URI.create("https://api.modrinth.com/v2/project/bluemapwebchat/version");
    private static final String MODRINTH_PAGE = "https://modrinth.com/plugin/bluemapwebchat";
    private static final String CURSEFORGE_PAGE = "https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat";
    private static final long INITIAL_DELAY_TICKS = 60L;
    private static final long CHECK_INTERVAL_TICKS = 12L * 60L * 60L * 20L;
    private static final long JOIN_NOTICE_DELAY_TICKS = 60L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final BlueMapWebChatPlugin plugin;
    private final HttpClient httpClient;
    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean requestRunning = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile UpdateInfo availableUpdate;
    private volatile String lastLoggedVersion = "";
    private BukkitTask checkTask;

    public UpdateChecker(BlueMapWebChatPlugin plugin) {
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
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> notifyPlayer(player), JOIN_NOTICE_DELAY_TICKS);
    }

    private void checkNow() {
        if (closed.get() || !requestRunning.compareAndSet(false, true)) return;
        try {
            String current = plugin.getDescription().getVersion();
            HttpRequest request = HttpRequest.newBuilder(MODRINTH_API)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "KOKOTO-DEV/BlueMapWebChat/" + current + " (" + MODRINTH_PAGE + ")")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                plugin.getLogger().fine("Update check skipped: Modrinth returned HTTP " + response.statusCode());
                return;
            }

            UpdateInfo newest = newestRelease(response.body());
            if (newest == null || compareVersions(newest.version, current) <= 0) {
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
                plugin.getLogger().info("BlueMapWebChat update available: " + newest.version
                        + " (current: " + current + ") - " + MODRINTH_PAGE);
            }

            if (!closed.get()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (closed.get()) return;
                    for (Player player : Bukkit.getOnlinePlayers()) notifyPlayer(player);
                });
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "BlueMapWebChat update check failed", ex);
        } finally {
            requestRunning.set(false);
        }
    }

    private boolean canReceiveNotice(Player player) {
        return player != null && player.isOnline() && player.hasPermission("bluemapwebchat.update.notify");
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
        line.addExtra(new TextComponent(ChatColor.AQUA + "[BlueMapWebChat] " + ChatColor.YELLOW + notice + " "));
        line.addExtra(linkButton("[" + modrinthLabel + "]", MODRINTH_PAGE, openHint));
        line.addExtra(new TextComponent(" "));
        line.addExtra(linkButton("[" + curseForgeLabel + "]", CURSEFORGE_PAGE, openHint));
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
        int[] a = coreVersion(left);
        int[] b = coreVersion(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int[] coreVersion(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("v")) value = value.substring(1);
        int suffix = value.indexOf('-');
        if (suffix >= 0) value = value.substring(0, suffix);
        int build = value.indexOf('+');
        if (build >= 0) value = value.substring(0, build);
        String[] parts = value.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9].*$", "");
            try {
                numbers[i] = digits.isBlank() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        notifiedPlayers.clear();
        availableUpdate = null;
    }

    static final class UpdateInfo {
        final String version;

        UpdateInfo(String version) {
            this.version = version;
        }
    }
}
