package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * ConfigMigrationManager는 이전 KWC/BMWC 설치 데이터를 현재 형식으로 옮기는 migration 코드다.
 * ConfigMigrationManager migrates older KWC/BMWC installation data into the current format.
 *
 * 기존 관리자 값을 가능한 한 보존하고, 한 번 적용한 migration을 재실행해도 결과가 달라지지 않는 idempotency를 유지해야 한다.
 * It should preserve administrator choices where possible and remain idempotent when the same migration is evaluated again.
 */
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
