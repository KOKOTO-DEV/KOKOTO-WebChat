package dev.kokoto.webchat;

import java.nio.file.Path;
import java.util.Map;

/** Bukkit-side bridge for the platform-neutral WebPushManager. */
public final class BukkitWebPushHost implements WebPushHost {
    private final KokotoWebChatPlugin plugin;

    public BukkitWebPushHost(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ConfigValues config() {
        return plugin.configValues();
    }

    @Override
    public Path dataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public Account findAccountByUuid(String uuid) {
        Storage storage = plugin.storage();
        return storage == null ? null : storage.findAccountById("uuid:" + String.valueOf(uuid == null ? "" : uuid));
    }

    @Override
    public PlayerIdentity findKnownPlayerByUuid(String uuid) {
        Storage storage = plugin.storage();
        return storage == null ? null : storage.findKnownPlayerByUuid(uuid);
    }

    @Override
    public Map<String, String> webStringsFor(String language) {
        LangManager lang = plugin.langManager();
        return lang == null ? Map.of() : lang.webStringsFor(language);
    }

    @Override
    public void fine(String message) {
        plugin.getLogger().fine(message);
    }

    @Override
    public void warn(String message) {
        plugin.getLogger().warning(message);
    }
}
