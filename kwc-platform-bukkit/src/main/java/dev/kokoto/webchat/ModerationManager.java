package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * ModerationManager는 메시지 moderation/content-filter 파이프라인의 일부다.
 * ModerationManager is part of the message moderation/content-filter pipeline.
 *
 * 필터 결과는 공개/DM/그룹 등 호출 위치에 따라 정책이 달라질 수 있으므로 parsing, matching, enforcement를 한 단계로 섞지 않는다.
 * Filter policy can vary by public/DM/group context, so parsing, matching, and enforcement should remain separate stages.
 */
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModerationManager implements WebChatModeration {
    private final KokotoWebChatPlugin plugin;
    private final File file;
    private final Map<String, ModerationEntry> entries = new ConcurrentHashMap<>();

    public ModerationManager(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "moderation.yml");
    }

    public synchronized void load() {
        entries.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("entries");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            ModerationEntry e = new ModerationEntry();
            e.key = key;
            e.type = s.getString("type", "guest");
            e.value = s.getString("value", "");
            e.reason = s.getString("reason", "");
            e.createdBy = s.getString("createdBy", "");
            e.createdAt = s.getLong("createdAt", System.currentTimeMillis());
            e.expiresAt = s.getLong("expiresAt", 0);
            if (!e.expired()) {
                entries.put(e.key, e);
            }
        }
        save();
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (ModerationEntry e : entries.values()) {
            if (e.expired()) continue;
            String p = "entries." + e.key + ".";
            yml.set(p + "type", e.type);
            yml.set(p + "value", e.value);
            yml.set(p + "reason", e.reason);
            yml.set(p + "createdBy", e.createdBy);
            yml.set(p + "createdAt", e.createdAt);
            yml.set(p + "expiresAt", e.expiresAt);
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save moderation.yml: " + ex.getMessage());
        }
    }

    public synchronized ModerationEntry mute(String type, String value, long minutes, String reason, String createdBy) {
        cleanup();
        type = normalizeType(type);
        value = normalizeValue(value);
        ModerationEntry e = new ModerationEntry();
        e.type = type;
        e.value = value;
        e.key = type + ":" + value.toLowerCase(Locale.ROOT);
        e.reason = reason == null ? "" : reason;
        e.createdBy = createdBy == null ? "" : createdBy;
        e.createdAt = System.currentTimeMillis();
        e.expiresAt = minutes <= 0 ? 0 : e.createdAt + minutes * 60_000L;
        entries.put(e.key, e);
        save();
        return e;
    }

    public synchronized boolean unmute(String type, String value) {
        cleanup();
        String key = normalizeType(type) + ":" + normalizeValue(value).toLowerCase(Locale.ROOT);
        boolean removed = entries.remove(key) != null;
        if (removed) save();
        return removed;
    }

    public boolean isMuted(String guestName, String ip) {
        cleanup();
        String g = "guest:" + normalizeValue(guestName).toLowerCase(Locale.ROOT);
        String i = "ip:" + normalizeValue(ip).toLowerCase(Locale.ROOT);
        return entries.containsKey(g) || entries.containsKey(i);
    }

    public ModerationEntry findMatch(String guestName, String ip) {
        cleanup();
        ModerationEntry e = entries.get("guest:" + normalizeValue(guestName).toLowerCase(Locale.ROOT));
        if (e != null) return e;
        return entries.get("ip:" + normalizeValue(ip).toLowerCase(Locale.ROOT));
    }

    public List<ModerationEntry> list() {
        cleanup();
        List<ModerationEntry> out = new ArrayList<>(entries.values());
        out.sort(Comparator.comparingLong(a -> a.createdAt));
        return out;
    }

    private synchronized void cleanup() {
        boolean changed = entries.entrySet().removeIf(e -> e.getValue().expired());
        if (changed) save();
    }

    private String normalizeType(String type) {
        if (type == null) return "guest";
        type = type.trim().toLowerCase(Locale.ROOT);
        if (!type.equals("ip")) return "guest";
        return type;
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }
}
