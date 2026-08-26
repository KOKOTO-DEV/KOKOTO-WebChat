package dev.kokoto.webchat;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bukkit/Spigot/Paper implementation of the loader-neutral platform boundary. */
public final class BukkitPlatformAdapter implements PlatformAdapter {
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)((?:https?://|www\\.)[^\\s<>\"]+)");
    private final KokotoWebChatPlugin plugin;

    public BukkitPlatformAdapter(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String platformName() {
        return plugin.getServer().getName();
    }

    @Override
    public String minecraftVersion() {
        return plugin.getServer().getBukkitVersion();
    }

    @Override
    public Path dataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public Collection<PlatformPlayer> onlinePlayers() {
        ArrayList<PlatformPlayer> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) out.add(snapshot(player));
        return out;
    }

    @Override
    public Optional<PlatformPlayer> onlinePlayer(UUID uuid) {
        if (uuid == null) return Optional.empty();
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? Optional.empty() : Optional.of(snapshot(player));
    }

    @Override
    public boolean hasPermission(UUID uuid, String permission) {
        if (uuid == null || permission == null || permission.isBlank()) return false;
        Player player = Bukkit.getPlayer(uuid);
        return player != null && PermissionCompat.has(player, permission);
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void runMainThread(Runnable task) {
        if (task == null) return;
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public boolean dispatchConsoleCommand(String command) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.valueOf(command == null ? "" : command));
    }

    @Override
    public Set<String> knownPlayerNames() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player != null && player.getName() != null && !player.getName().isBlank()) out.add(player.getName());
        }
        return out;
    }

    @Override
    public Set<String> knownPlayerNameAliases() {
        LinkedHashSet<String> out = new LinkedHashSet<>(knownPlayerNames());

        // Keep the raw Bukkit aliases as well as the configured rendered name.
        // The core spoof check strips Minecraft formatting independently of the
        // player-display.strip-colors setting.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) continue;
            addProtectedName(out, player.getName());
            addProtectedName(out, player.getDisplayName());
            addProtectedName(out, player.getCustomName());
            addProtectedName(out, plugin.displayPlayerName(player));
        }

        try {
            for (PlayerIdentity player : plugin.storage().listKnownPlayers("", 0)) {
                if (player == null || player.uuid == null || RemotePlayerRef.isRemote(player.uuid)) continue;
                addProtectedName(out, player.username);
                addProtectedName(out, player.displayName);
            }
        } catch (Throwable ignored) {
            // Bukkit's offline-player list above still protects real usernames
            // if remembered display-name storage is temporarily unavailable.
        }
        return out;
    }

    private static void addProtectedName(Set<String> out, String name) {
        if (out == null || name == null || name.isBlank()) return;
        out.add(name);
    }

    @Override
    public void sendPlainMessage(UUID uuid, String message) {
        if (uuid == null) return;
        Runnable delivery = () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) player.sendMessage(String.valueOf(message == null ? "" : message));
        };
        if (Bukkit.isPrimaryThread()) delivery.run(); else Bukkit.getScheduler().runTask(plugin, delivery);
    }

    @Override
    public void broadcastPlainMessage(String message) {
        Runnable delivery = () -> Bukkit.broadcastMessage(String.valueOf(message == null ? "" : message));
        if (Bukkit.isPrimaryThread()) delivery.run(); else Bukkit.getScheduler().runTask(plugin, delivery);
    }

    @Override
    public void sendInteractiveMessage(Collection<UUID> recipients, PlatformGameMessage message) {
        if (message == null) return;
        Collection<UUID> ids = recipients == null ? null : new ArrayList<>(recipients);
        Runnable delivery = () -> {
            TextComponent component = buildInteractiveLine(message);
            Collection<UUID> targetIds = ids == null ? onlinePlayerIds() : ids;
            for (UUID uuid : targetIds) {
                if (uuid == null) continue;
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) player.spigot().sendMessage(component);
            }
        };
        if (Bukkit.isPrimaryThread()) delivery.run(); else Bukkit.getScheduler().runTask(plugin, delivery);
    }

    @Override
    public void broadcastInteractiveMessage(PlatformGameMessage message) {
        if (message == null) return;
        Runnable delivery = () -> {
            TextComponent component = buildInteractiveLine(message);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player != null && player.isOnline()) player.spigot().sendMessage(component);
            }
            if (Bukkit.getConsoleSender() != null) Bukkit.getConsoleSender().sendMessage(message.text());
        };
        if (Bukkit.isPrimaryThread()) delivery.run(); else Bukkit.getScheduler().runTask(plugin, delivery);
    }

    @Override
    public Map<String, String> imageEmojiRuntimeSymbols() {
        if (!Bukkit.isPrimaryThread()) return Map.of();
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try {
            for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
                if (candidate == null || !candidate.isEnabled()) continue;
                String pluginName = String.valueOf(candidate.getName() == null ? "" : candidate.getName());
                String descriptionName = candidate.getDescription() == null ? "" : String.valueOf(candidate.getDescription().getName());
                String normalized = (pluginName + descriptionName).toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
                if (!normalized.contains("imageemojis") && !normalized.contains("imageemoji")) continue;

                Object repository = invokeNoArg(candidate, "getEmojiRepository");
                Object emojis = invokeNoArg(repository, "getEmojis");
                if (!(emojis instanceof Iterable<?> iterable)) continue;
                for (Object emoji : iterable) {
                    if (emoji == null) continue;
                    String symbol = reflectString(emoji, "getAsUtf8Symbol");
                    if (symbol.isBlank()) continue;
                    addEmojiSymbol(out, reflectString(emoji, "getName"), symbol);
                    addEmojiSymbol(out, reflectString(emoji, "getTemplate"), symbol);
                    addEmojiSymbol(out, reflectString(emoji, "getFileName"), symbol);
                }
            }
        } catch (Throwable ignored) {
        }
        return out.isEmpty() ? Map.of() : Map.copyOf(out);
    }

    private Collection<UUID> onlinePlayerIds() {
        ArrayList<UUID> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) if (player != null) out.add(player.getUniqueId());
        return out;
    }

    private static TextComponent buildInteractiveLine(PlatformGameMessage message) {
        TextComponent root = new TextComponent("");
        String text = message.text();
        int senderIndex = message.senderTarget().isBlank() ? -1 : text.indexOf(message.senderTarget());
        if (senderIndex < 0) {
            appendReplyAwareText(root, text, message);
            return root;
        }

        appendReplyAwareText(root, text.substring(0, senderIndex), withoutSender(message));
        appendInteractiveLegacy(root, message.senderTarget(), message.senderHoverText(), message.senderSuggestCommand());
        appendReplyAwareText(root, text.substring(senderIndex + message.senderTarget().length()), withoutSender(message));
        return root;
    }

    private static PlatformGameMessage withoutSender(PlatformGameMessage message) {
        return new PlatformGameMessage(message.text(), message.clickableUrls(), "", "", "",
                message.replyTarget(), message.replyHoverText(), message.replySuggestCommand());
    }

    private static void appendReplyAwareText(TextComponent root, String text, PlatformGameMessage message) {
        if (text == null || text.isEmpty()) return;
        if (message.replyTarget().isBlank() || message.replySuggestCommand().isBlank()) {
            appendLegacyWithUrls(root, text, message.clickableUrls(), null, null);
            return;
        }
        int index = text.lastIndexOf(message.replyTarget());
        if (index < 0) {
            appendLegacyWithUrls(root, text, message.clickableUrls(), null, null);
            return;
        }
        appendLegacyWithUrls(root, text.substring(0, index), message.clickableUrls(), null, null);
        appendLegacyWithUrls(root, message.replyTarget(), message.clickableUrls(), message.replyHoverText(), message.replySuggestCommand());
        if (message.clickableUrls() && containsOnlyClickableUrls(message.replyTarget())) {
            appendInteractiveLegacy(root, " §8[↩]", message.replyHoverText(), message.replySuggestCommand());
        }
        appendLegacyWithUrls(root, text.substring(index + message.replyTarget().length()), message.clickableUrls(), null, null);
    }

    private static boolean containsOnlyClickableUrls(String text) {
        String value = String.valueOf(text == null ? "" : text).trim();
        if (value.isEmpty()) return false;
        Matcher matcher = URL_PATTERN.matcher(value);
        if (!matcher.find()) return false;
        return matcher.replaceAll("").trim().isEmpty();
    }

    private static void appendLegacyWithUrls(TextComponent root, String text, boolean clickableUrls, String hoverText, String suggestCommand) {
        if (text == null || text.isEmpty()) return;
        if (!clickableUrls) {
            appendInteractiveLegacy(root, text, hoverText, suggestCommand);
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        int last = 0;
        while (matcher.find()) {
            appendInteractiveLegacy(root, text.substring(last, matcher.start()), hoverText, suggestCommand);
            String[] split = splitUrlTrailing(matcher.group(1));
            String url = split[0];
            if (!url.isBlank()) {
                String clickUrl = normalizeClickUrl(url);
                for (BaseComponent part : TextComponent.fromLegacyText(url)) {
                    part.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, clickUrl));
                    root.addExtra(part);
                }
            }
            appendInteractiveLegacy(root, split[1], hoverText, suggestCommand);
            last = matcher.end();
        }
        appendInteractiveLegacy(root, text.substring(last), hoverText, suggestCommand);
    }

    private static void appendInteractiveLegacy(TextComponent root, String text, String hoverText, String suggestCommand) {
        if (text == null || text.isEmpty()) return;
        HoverEvent hoverEvent = hoverText == null || hoverText.isBlank()
                ? null : new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hoverText).create());
        ClickEvent clickEvent = suggestCommand == null || suggestCommand.isBlank()
                ? null : new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestCommand);
        for (BaseComponent part : TextComponent.fromLegacyText(text)) {
            if (hoverEvent != null) part.setHoverEvent(hoverEvent);
            if (clickEvent != null) part.setClickEvent(clickEvent);
            root.addExtra(part);
        }
    }

    private static String normalizeClickUrl(String url) {
        String lower = String.valueOf(url == null ? "" : url).toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") ? url : "https://" + url;
    }

    private static String[] splitUrlTrailing(String raw) {
        String url = raw == null ? "" : raw;
        StringBuilder trailing = new StringBuilder();
        while (!url.isEmpty()) {
            char ch = url.charAt(url.length() - 1);
            if (ch == '.' || ch == ',' || ch == '!' || ch == '?' || ch == ';' || ch == ':'
                    || ch == ')' || ch == ']' || ch == '}') {
                trailing.insert(0, ch);
                url = url.substring(0, url.length() - 1);
            } else break;
        }
        return new String[]{url, trailing.toString()};
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String reflectString(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void addEmojiSymbol(Map<String, String> out, String rawKey, String symbol) {
        String key = String.valueOf(rawKey == null ? "" : rawKey).trim();
        if (key.isBlank() || symbol == null || symbol.isBlank()) return;
        out.putIfAbsent(key, symbol);
    }

    private PlatformPlayer snapshot(Player player) {
        return new PlatformPlayer(player.getUniqueId(), player.getName(), plugin.displayPlayerName(player), player.isOp());
    }
}
