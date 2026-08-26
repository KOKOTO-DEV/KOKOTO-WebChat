package dev.kokoto.webchat;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class EventAnnouncementListener implements Listener {
    private final KokotoWebChatPlugin plugin;

    public EventAnnouncementListener(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Map<String, String> values = playerValues(player);
        if (!player.hasPlayedBefore() && plugin.configValues().announcementEnabled("first-join")) {
            plugin.publishAnnouncement("first-join", values);
            return;
        }
        plugin.publishAnnouncement("minecraft-join", values);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.publishAnnouncement("minecraft-quit", playerValues(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Map<String, String> values = playerValues(player);
        String deathMessage = event.getDeathMessage();
        if (deathMessage == null || deathMessage.isBlank()) {
            deathMessage = plugin.displayPlayerName(player) + " died.";
        }
        deathMessage = plain(deathMessage);
        Player killer = player.getKiller();
        values.put("message", deathMessage);
        values.put("killer", killer == null ? "" : plugin.displayPlayerName(killer));
        plugin.publishAnnouncement("death", values);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Advancement advancement = event.getAdvancement();
        NamespacedKey key = advancement.getKey();
        String keyText = key == null ? "" : key.toString();
        if (isNoisyAdvancement(keyText)) return;

        Map<String, String> values = playerValues(event.getPlayer());
        values.put("advancement_key", keyText);
        values.put("advancement", prettyAdvancementName(keyText));
        plugin.publishAnnouncement("advancement", values);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom();
        World to = player.getWorld();
        Map<String, String> values = playerValues(player);
        values.put("from_world", from == null ? "" : from.getName());
        values.put("to_world", to == null ? "" : to.getName());
        values.put("world", to == null ? "" : to.getName());
        plugin.publishAnnouncement("world-change", values);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        Map<String, String> values = playerValues(player);
        values.put("from_gamemode", prettyEnumName(player.getGameMode().name()));
        values.put("to_gamemode", prettyEnumName(event.getNewGameMode().name()));
        values.put("gamemode", prettyEnumName(event.getNewGameMode().name()));
        plugin.publishAnnouncement("gamemode-change", values);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelChange(PlayerLevelChangeEvent event) {
        Map<String, String> values = playerValues(event.getPlayer());
        values.put("old_level", Integer.toString(event.getOldLevel()));
        values.put("new_level", Integer.toString(event.getNewLevel()));
        values.put("level", Integer.toString(event.getNewLevel()));
        plugin.publishAnnouncement("level-change", values);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        plugin.publishAnnouncement("bed-enter", playerValues(event.getPlayer()));
    }

    private Map<String, String> playerValues(Player player) {
        Map<String, String> values = new LinkedHashMap<>();
        if (player == null) {
            values.put("player", "");
            values.put("name", "");
            values.put("uuid", "");
            values.put("world", "");
            return values;
        }
        values.put("player", plugin.displayPlayerName(player));
        values.put("name", plugin.displayPlayerName(player));
        values.put("real_player", player.getName());
        values.put("real_name", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("world", player.getWorld() == null ? "" : player.getWorld().getName());
        return values;
    }

    private boolean isNoisyAdvancement(String key) {
        if (key == null || key.isBlank()) return true;
        String k = key.toLowerCase(Locale.ROOT);
        return k.startsWith("minecraft:recipes/") || k.contains(":recipes/");
    }

    private String prettyAdvancementName(String key) {
        if (key == null || key.isBlank()) return "Unknown";
        String name = key;
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        int colon = name.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < name.length()) name = name.substring(colon + 1);
        name = name.replace('_', ' ').trim();
        if (name.isEmpty()) return key;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String prettyEnumName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String plain(String text) {
        if (text == null) return "";
        String translated = ChatColor.translateAlternateColorCodes('&', text);
        String stripped = ChatColor.stripColor(translated);
        return stripped == null ? "" : stripped;
    }
}
