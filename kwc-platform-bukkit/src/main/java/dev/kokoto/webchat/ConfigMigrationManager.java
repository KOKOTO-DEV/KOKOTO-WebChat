package dev.kokoto.webchat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bukkit adapter for the loader-neutral migration engine.
 *
 * Keeping the physical migration/report implementation in kwc-core prevents the
 * Bukkit path from drifting away from Fabric/NeoForge/Forge behavior.
 */
final class ConfigMigrationManager {
    private ConfigMigrationManager() {}

    static void check(KokotoWebChatPlugin plugin) {
        try {
            PortableConfigMigration.Result result = PortableConfigMigration.reconcile(
                    plugin.getDataFolder().toPath(),
                    String.valueOf(plugin.getDescription().getVersion()).trim(),
                    plugin::getResource,
                    ConfigMigrationManager::loadSnapshot,
                    message -> plugin.getLogger().info(message)
            );
            if (result.changed()) plugin.reloadConfig();
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to migrate/review config.yml: " + ex.getMessage());
        }
    }

    private static Map<String,Object> loadSnapshot(InputStream input) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            return leafValues(yaml);
        }
    }

    private static Map<String,Object> leafValues(YamlConfiguration yaml) {
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        Set<String> paths = new LinkedHashSet<>(yaml.getKeys(true));
        for (String path : paths) {
            if (yaml.isConfigurationSection(path)) {
                ConfigurationSection section = yaml.getConfigurationSection(path);
                if (section != null && section.getKeys(false).isEmpty()) out.put(path, new LinkedHashMap<>());
                continue;
            }
            out.put(path, normalizeValue(yaml.get(path)));
        }
        return out;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            LinkedHashMap<String,Object> out = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) out.put(key, normalizeValue(section.get(key)));
            return out;
        }
        if (value instanceof Map<?,?> map) {
            LinkedHashMap<String,Object> out = new LinkedHashMap<>();
            for (Map.Entry<?,?> entry : map.entrySet()) out.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(normalizeValue(item));
            return out;
        }
        return value;
    }
}
